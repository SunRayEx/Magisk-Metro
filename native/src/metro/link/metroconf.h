/*
 * metroconf.h — C ABI of the Rust declarative manifest parser (crate `metroconf`).
 * Shared by the Zig orchestrator (main.zig) and the C plumbing (shim.c) of metrolink.
 */

#ifndef METROCONF_H
#define METROCONF_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Fixed-size entry handed across the C ABI. Buffers are NUL-terminated. */
typedef struct {
    char id[160];
    char name[160];
    char version[96];
    int64_t version_code;
    int32_t enabled;
    int32_t _pad;
} MetroEntry;

/* Error codes shared with Zig. */
#define METRO_OK        0
#define METRO_ERR_NOMEM  (-1)
#define METRO_ERR_ARG    (-2)
#define METRO_ERR_PARSE  (-3)
#define METRO_ERR_IO     (-4)

/*
 * Handles are opaque (u64). Android heap pointers carry a tag byte in the top bits and look
 * negative through a signed integer, so 0 is the only invalid handle value.
 */
uint64_t metroconf_new(void);
uint64_t metroconf_parse(const uint8_t *data, size_t len);
void     metroconf_close(uint64_t handle);
int64_t  metroconf_serialize(uint64_t handle, uint8_t *buf, size_t cap);
int64_t  metroconf_count(uint64_t handle);
int32_t  metroconf_entry(uint64_t handle, int64_t index, MetroEntry *out);
int32_t  metroconf_upsert(uint64_t handle, const MetroEntry *entry);
int32_t  metroconf_remove(uint64_t handle, const uint8_t *id);
int32_t  metroconf_set_enabled(uint64_t handle, const uint8_t *id, int32_t enabled);
int32_t  metroconf_set_updated(uint64_t handle, int64_t updated_at);
int32_t  metroconf_error(uint8_t *buf, size_t cap);

#ifdef __cplusplus
}
#endif

#endif /* METROCONF_H */
