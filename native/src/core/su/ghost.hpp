#pragma once

/*
 * ghost.cpp — seccomp BPF interceptor with invisible ("ghost") auditing.
 * Used by level 0 su shells and the DenyList sandbox.
 */

/* Opens (as root) the append-only ghost audit log for this process tree. */
void ghost_open_audit(const char *path);

/* Installs the seccomp BPF filter intercepting high-risk syscalls. */
void ghost_install_filter();

/* One-shot helper: install the filter without an audit channel. */
void ghost_install();

/* Append-only audit trail for intercepted syscalls (written only by root). */
#define GHOST_AUDIT_LOG "/data/adb/metromod/ghost_audit.log"
/* Flag file read by zygisk to decide whether deny-listed apps get sandboxed. */
#define GHOST_SANDBOX_FLAG "/data/adb/metromod/ghost_sandbox" 
