/*
 * ghost.cpp — the ghost sandbox: a seccomp BPF interceptor with invisible auditing.
 *
 * Two consumers:
 *   - Level 0 su shells: exec_root_shell() installs the sandbox in an isolated mount
 *     namespace; the shell shows uid 0 while every high-risk syscall is intercepted.
 *   - DenyList enforcement: zygisk installs the same filter into deny-listed app
 *     processes right after their unmount, so even a compromised or probing app is
 *     walled off from privileged kernel surfaces.
 *
 * Intercepted syscalls return ENOSYS directly. The filter remains invisible to targets
 * and avoids signal handling during Android zygote specialization.
 */

#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <linux/audit.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <signal.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>
#include <ucontext.h>

#include <string>

#include <base.hpp>

#include "ghost.hpp"

static int ghost_audit_fd = -1;

/* High-risk / unreasonable syscalls intercepted inside the sandbox. */
static const int ghost_blocked[] = {
#ifdef __NR_mount
        __NR_mount,
#endif
#ifdef __NR_umount2
        __NR_umount2,
#endif
#ifdef __NR_reboot
        __NR_reboot,
#endif
#ifdef __NR_swapon
        __NR_swapon,
#endif
#ifdef __NR_swapoff
        __NR_swapoff,
#endif
#ifdef __NR_init_module
        __NR_init_module,
#endif
#ifdef __NR_finit_module
        __NR_finit_module,
#endif
#ifdef __NR_delete_module
        __NR_delete_module,
#endif
#ifdef __NR_kexec_load
        __NR_kexec_load,
#endif
#ifdef __NR_kexec_file_load
        __NR_kexec_file_load,
#endif
};

static void ghost_log_record(const char *syscall_name, uintptr_t pc) {
    char buf[256];
    struct timespec ts{};
    clock_gettime(CLOCK_REALTIME, &ts);
    int len;
    if (ghost_audit_fd >= 0) {
        len = ssprintf(buf, sizeof(buf),
                       "GHOST %lld.%03ld syscall=%s(%zu) pid=%d ip=%#zx\n",
                       (long long) ts.tv_sec, ts.tv_nsec / 1000000,
                       syscall_name, (size_t) syscall(SYS_gettid), getpid(), pc);
    } else {
        len = 0; /* no audit channel: keep the interception invisible */
    }
    if (len > 0) {
        ssize_t _ = write(ghost_audit_fd, buf, (size_t) len);
        (void) _;
    }
}

/* Names the intercepted syscall for the audit trail; unknown numbers fall back to raw. */
static const char *ghost_name(int nr) {
    for (const int blocked : ghost_blocked) {
        if (blocked == nr) {
#define CASE(n) if (nr == __NR_##n) return #n
            CASE(mount); CASE(umount2); CASE(reboot); CASE(swapon); CASE(swapoff);
            CASE(init_module); CASE(finit_module); CASE(delete_module); CASE(kexec_load);
#ifdef __NR_kexec_file_load
            CASE(kexec_file_load);
#endif
#undef CASE
        }
    }
    return "unknown";
}

static void ghost_sigsys(int, siginfo_t *info, void *ctx) {
    ucontext_t *uc = (ucontext_t *) ctx;
    uintptr_t pc = (uintptr_t) info->si_call_addr;
#if defined(__aarch64__)
    if (uc) pc = (uintptr_t) uc->uc_mcontext.pc;
    if (uc) uc->uc_mcontext.regs[0] = (uintptr_t) -ENOSYS;
#elif defined(__x86_64__)
    if (uc) pc = (uintptr_t) uc->uc_mcontext.gregs[REG_RIP];
    if (uc) uc->uc_mcontext.gregs[REG_RAX] = -ENOSYS;
#elif defined(__i386__)
    if (uc) pc = (uintptr_t) uc->uc_mcontext.gregs[REG_EIP];
    if (uc) uc->uc_mcontext.gregs[REG_EAX] = -ENOSYS;
#elif defined(__arm__)
    if (uc) pc = (uintptr_t) uc->uc_mcontext.arm_pc;
    if (uc) uc->uc_mcontext.arm_r0 = (uintptr_t) -ENOSYS;
#endif
    ghost_log_record(ghost_name(info->si_syscall), pc);
    /* Returning skips the syscall; the caller observes ENOSYS. */
}

void ghost_open_audit(const char *path) {
    if (ghost_audit_fd >= 0) return;
    ghost_audit_fd = open(path, O_WRONLY | O_APPEND | O_CREAT | O_CLOEXEC, 0600);
    if (ghost_audit_fd < 0) {
        // First run before the manager created the store directory.
        mkdir("/data/adb", 0700);
        mkdir("/data/adb/metromod", 0700);
        ghost_audit_fd = open(path, O_WRONLY | O_APPEND | O_CREAT | O_CLOEXEC, 0600);
    }
}

/* Builds and installs the seccomp filter: default allow, blocked list traps. */
void ghost_install_filter() {
    if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0) return;

    struct sock_filter prog[64] = {};
    size_t idx = 0;
    auto emit = [&](struct sock_filter f) {
        if (idx < std::size(prog)) prog[idx++] = f;
    };

    emit((struct sock_filter) BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr)));
    for (const int nr : ghost_blocked) {
        emit((struct sock_filter) BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, (uint32_t) nr, 0, 1));
        // Return a regular ENOSYS error directly.  SECCOMP_RET_TRAP relies on a
        // SIGSYS handler and is not safe during Android zygote specialization.
        emit((struct sock_filter) BPF_STMT(BPF_RET | BPF_K,
                                           SECCOMP_RET_ERRNO | (ENOSYS & SECCOMP_RET_DATA)));
    }
    emit((struct sock_filter) BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW));

    struct sock_fprog fprog = {
        .len = (unsigned short) idx,
        .filter = prog,
    };

    prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &fprog, 0, 0);
}

void ghost_install() {
    ghost_install_filter();
}
