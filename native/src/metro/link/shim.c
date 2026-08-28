/*
 * shim.c — C plumbing for metrolink, the persistent-module link manager.
 *
 * The Zig orchestrator owns command dispatch and manifest reconciliation; every syscall-level
 * file operation lives here so the surface crossed between Zig and C is small and auditable.
 */

#include "metroconf.h"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

void metro_free(void *ptr) {
    free(ptr);
}

/*
 * Weak no-op fallbacks for unwinder symbols that Rust std objects compiled with unwind tables
 * may reference. The manifest parser builds with panic=immediate-abort so no unwinding ever
 * happens; these only satisfy the static linker when no real libunwind is linked (pure-zig
 * link). A real libunwind, when present, overrides the weak definitions.
 */
struct _Unwind_Context;
struct _Unwind_Exception;
typedef int _Unwind_Action;
typedef int _Unwind_Reason_Code;

__attribute__((weak)) void _Unwind_Resume(struct _Unwind_Exception *e) { (void) e; }
__attribute__((weak)) _Unwind_Reason_Code _Unwind_RaiseException(struct _Unwind_Exception *e) { (void) e; return 0; }
__attribute__((weak)) uintptr_t _Unwind_GetIP(struct _Unwind_Context *ctx) { (void) ctx; return 0; }
__attribute__((weak)) void _Unwind_SetIP(struct _Unwind_Context *ctx, uintptr_t ip) { (void) ctx; (void) ip; }
__attribute__((weak)) uintptr_t _Unwind_GetGR(struct _Unwind_Context *ctx, int i) { (void) ctx; (void) i; return 0; }
__attribute__((weak)) void _Unwind_SetGR(struct _Unwind_Context *ctx, int i, uintptr_t v) { (void) ctx; (void) i; (void) v; }
__attribute__((weak)) uintptr_t _Unwind_GetRegionStart(struct _Unwind_Context *ctx) { (void) ctx; return 0; }
__attribute__((weak)) uintptr_t _Unwind_GetTextRelBase(struct _Unwind_Context *ctx) { (void) ctx; return 0; }
__attribute__((weak)) uintptr_t _Unwind_GetDataRelBase(struct _Unwind_Context *ctx) { (void) ctx; return 0; }
__attribute__((weak)) uintptr_t _Unwind_GetLanguageSpecificData(struct _Unwind_Context *ctx) { (void) ctx; return 0; }
__attribute__((weak)) _Unwind_Reason_Code _Unwind_Backtrace(void (*trace)(struct _Unwind_Context *, void *), void *data) { (void) trace; (void) data; return 0; }
__attribute__((weak)) uintptr_t _Unwind_GetIPInfo(struct _Unwind_Context *ctx, int *ip_before) { (void) ctx; if (ip_before) *ip_before = 0; return 0; }

int metro_mkdirs(const char *path) {
    char buf[PATH_MAX];
    if (!path || strlen(path) >= sizeof(buf)) return METRO_ERR_ARG;
    snprintf(buf, sizeof(buf), "%s", path);
    size_t len = strlen(buf);
    while (len > 1 && buf[len - 1] == '/') buf[--len] = '\0';

    for (char *p = buf + 1; *p; p++) {
        if (*p == '/') {
            *p = '\0';
            if (mkdir(buf, 0700) != 0 && errno != EEXIST) return METRO_ERR_IO;
            *p = '/';
        }
    }
    if (mkdir(buf, 0700) != 0 && errno != EEXIST) return METRO_ERR_IO;
    return METRO_OK;
}

int metro_dir_exists(const char *path) {
    struct stat st;
    if (stat(path, &st) != 0 || !S_ISDIR(st.st_mode)) return 0;
    return 1;
}

int metro_file_exists(const char *path) {
    struct stat st;
    if (lstat(path, &st) != 0) return 0;
    return 1;
}

int metro_unlink(const char *path) {
    if (unlink(path) != 0 && errno != ENOENT) return METRO_ERR_IO;
    return METRO_OK;
}

int metro_touch(const char *path) {
    int fd = open(path, O_WRONLY | O_CREAT | O_CLOEXEC, 0644);
    if (fd < 0) return METRO_ERR_IO;
    close(fd);
    return METRO_OK;
}

/* Reads a whole file into a malloc'ed buffer returned through `out`; byte count or negative. */
long metro_read_file(const char *path, char **out) {
    *out = NULL;
    FILE *f = fopen(path, "rb");
    if (!f) return METRO_ERR_IO;
    if (fseek(f, 0, SEEK_END) != 0) { fclose(f); return METRO_ERR_IO; }
    long size = ftell(f);
    if (size < 0) { fclose(f); return METRO_ERR_IO; }
    rewind(f);
    char *buf = malloc((size_t) size + 1);
    if (!buf) { fclose(f); return METRO_ERR_NOMEM; }
    if (fread(buf, 1, (size_t) size, f) != (size_t) size) {
        free(buf);
        fclose(f);
        return METRO_ERR_IO;
    }
    buf[size] = '\0';
    fclose(f);
    *out = buf;
    return size;
}

/* Atomic replace: write sibling temp file, fsync, rename over the destination. */
int metro_write_file_atomic(const char *path, const uint8_t *data, size_t len) {
    char tmp[PATH_MAX];
    if (snprintf(tmp, sizeof(tmp), "%s.tmp", path) >= (int) sizeof(tmp)) return METRO_ERR_ARG;
    int fd = open(tmp, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    if (fd < 0) return METRO_ERR_IO;
    size_t written = 0;
    while (written < len) {
        ssize_t n = write(fd, data + written, len - written);
        if (n <= 0) { close(fd); unlink(tmp); return METRO_ERR_IO; }
        written += (size_t) n;
    }
    fsync(fd);
    close(fd);
    if (rename(tmp, path) != 0) {
        unlink(tmp);
        return METRO_ERR_IO;
    }
    return METRO_OK;
}

/* Removes a tree of files/dirs/symlinks. */
static int remove_tree_impl(const char *path) {
    struct stat st;
    if (lstat(path, &st) != 0) return errno == ENOENT ? METRO_OK : METRO_ERR_IO;
    if (!S_ISDIR(st.st_mode)) return unlink(path) == 0 ? METRO_OK : METRO_ERR_IO;

    DIR *dir = opendir(path);
    if (!dir) return METRO_ERR_IO;
    struct dirent *entry;
    int rc = METRO_OK;
    while ((entry = readdir(dir)) != NULL) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;
        char child[PATH_MAX];
        if (snprintf(child, sizeof(child), "%s/%s", path, entry->d_name) >= (int) sizeof(child)) {
            rc = METRO_ERR_ARG;
            break;
        }
        rc = remove_tree_impl(child);
        if (rc != METRO_OK) break;
    }
    closedir(dir);
    if (rc == METRO_OK && rmdir(path) != 0) rc = METRO_ERR_IO;
    return rc;
}

int metro_remove_tree(const char *path) {
    return remove_tree_impl(path);
}

/* Copies one file, preserving mode and symlink-ness. */
static int copy_file(const char *src, const char *dst, const struct stat *st) {
    if (S_ISLNK(st->st_mode)) {
        char target[PATH_MAX];
        ssize_t len = readlink(src, target, sizeof(target) - 1);
        if (len < 0) return METRO_ERR_IO;
        target[len] = '\0';
        unlink(dst);
        if (symlink(target, dst) != 0) return METRO_ERR_IO;
        return METRO_OK;
    }

    int in = open(src, O_RDONLY | O_CLOEXEC);
    if (in < 0) return METRO_ERR_IO;
    unlink(dst);
    int out = open(dst, O_WRONLY | O_CREAT | O_CLOEXEC, st->st_mode & 0777);
    if (out < 0) { close(in); return METRO_ERR_IO; }

    char buf[64 * 1024];
    ssize_t n;
    int rc = METRO_OK;
    while ((n = read(in, buf, sizeof(buf))) > 0) {
        ssize_t written = 0;
        while (written < n) {
            ssize_t w = write(out, buf + written, (size_t)(n - written));
            if (w <= 0) { rc = METRO_ERR_IO; break; }
            written += w;
        }
        if (rc != METRO_OK) break;
    }
    if (n < 0) rc = METRO_ERR_IO;
    close(in);
    close(out);
    return rc;
}

/* Recursively copies src into dst (dst may not exist). Skips nothing: snapshots are exact. */
static int copy_tree_impl(const char *src, const char *dst) {
    struct stat st;
    if (lstat(src, &st) != 0) return METRO_ERR_IO;

    if (!S_ISDIR(st.st_mode)) return copy_file(src, dst, &st);

    if (mkdir(dst, st.st_mode & 0777) != 0 && errno != EEXIST) return METRO_ERR_IO;

    DIR *dir = opendir(src);
    if (!dir) return METRO_ERR_IO;
    struct dirent *entry;
    int rc = METRO_OK;
    while ((entry = readdir(dir)) != NULL) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;
        char src_child[PATH_MAX];
        char dst_child[PATH_MAX];
        if (snprintf(src_child, sizeof(src_child), "%s/%s", src, entry->d_name) >= (int) sizeof(src_child) ||
            snprintf(dst_child, sizeof(dst_child), "%s/%s", dst, entry->d_name) >= (int) sizeof(dst_child)) {
            rc = METRO_ERR_ARG;
            break;
        }
        rc = copy_tree_impl(src_child, dst_child);
        if (rc != METRO_OK) break;
    }
    closedir(dir);
    return rc;
}

int metro_copy_tree(const char *src, const char *dst) {
    if (metro_mkdirs(dst) != METRO_OK) {
        /* metro_mkdirs creates the final component too; that is fine, copy overwrites into it. */
        /* Fall through: copy_tree_impl tolerates an existing destination dir. */
    }
    return copy_tree_impl(src, dst);
}

/* Looks up `key` in a module.prop style file; returns malloc'ed value through `out`. */
int metro_prop_get(const char *path, const char *key, char **out) {
    *out = NULL;
    FILE *f = fopen(path, "rb");
    if (!f) return METRO_ERR_IO;

    char line[1024];
    size_t key_len = strlen(key);
    int rc = METRO_ERR_IO;
    while (fgets(line, sizeof(line), f)) {
        char *eq = strchr(line, '=');
        if (!eq) continue;
        *eq = '\0';
        char *name = line;
        while (*name == ' ' || *name == '\t') name++;
        char *value = eq + 1;
        size_t value_len = strlen(value);
        while (value_len > 0 && (value[value_len - 1] == '\n' || value[value_len - 1] == '\r' ||
                                 value[value_len - 1] == ' ' || value[value_len - 1] == '\t')) {
            value[--value_len] = '\0';
        }
        if (strlen(name) == key_len && strncmp(name, key, key_len) == 0) {
            char *dup = strdup(value);
            if (!dup) { rc = METRO_ERR_NOMEM; break; }
            *out = dup;
            rc = METRO_OK;
            break;
        }
    }
    fclose(f);
    return rc;
}

/*
 * Lists the immediate child names of a directory into a malloc'ed flat block of
 * NUL-separated names, terminated by an empty name. Caller frees via metro_free.
 * Returns byte length (including terminator) or a negative error.
 */
long metro_list_dir(const char *path, char **out) {
    *out = NULL;
    DIR *dir = opendir(path);
    if (!dir) return errno == ENOENT ? 0 : METRO_ERR_IO;

    size_t cap = 256, len = 0;
    char *buf = malloc(cap);
    if (!buf) { closedir(dir); return METRO_ERR_NOMEM; }

    struct dirent *entry;
    while ((entry = readdir(dir)) != NULL) {
        if (!strcmp(entry->d_name, ".") || !strcmp(entry->d_name, "..")) continue;
        size_t name_len = strlen(entry->d_name) + 1;
        while (len + name_len + 1 > cap) {
            cap *= 2;
            char *grown = realloc(buf, cap);
            if (!grown) { free(buf); closedir(dir); return METRO_ERR_NOMEM; }
            buf = grown;
        }
        memcpy(buf + len, entry->d_name, name_len);
        len += name_len;
    }
    closedir(dir);
    buf[len] = '\0'; /* terminator: empty name */
    *out = buf;
    return (long)(len + 1);
}

/* Wall clock seconds, used to stamp manifest updates. */
int64_t metro_now(void) {
    return (int64_t) time(NULL);
}

/* Bridge from the C runtime entry into the Zig orchestrator. */
int metrolink_main(int argc, char **argv);

int main(int argc, char **argv) {
    return metrolink_main(argc, argv);
}
