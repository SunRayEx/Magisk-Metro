//! metrolink — persistent-module link manager for MagisKube Metro.
//!
//! Command dispatch and manifest reconciliation live here (Zig); syscall-level plumbing lives
//! in shim.c (C); manifest parsing/serialization lives in the Rust crate `metroconf`, reached
//! through its C ABI. The manifest is the single declarative source of truth:
//!
//!   /data/adb/metromod/manifest.json    immutable declaration of the pinned module set
//!   /data/adb/metromod/store/<id>/      immutable snapshot of each pinned module
//!   /data/adb/modules/<id>/             live Magisk module directory (reconciled)
//!
//! Usage: metrolink apply | list | snapshot <id> | unpin <id> | toggle <id> <0|1> | prune

const std = @import("std");

/// Minimal panic policy: no stack traces, no TLS, no unwinding — abort via syscall.
/// Keeps the object file linkable against bionic, which lacks the glibc unwind TLS ABI.
fn metroPanic(msg: []const u8, _: ?usize) noreturn {
    const stderr_fd = 2;
    _ = std.os.linux.write(stderr_fd, "metrolink: panic: ", "metrolink: panic: ".len);
    _ = std.os.linux.write(stderr_fd, msg.ptr, msg.len);
    _ = std.os.linux.write(stderr_fd, "\n", 1);
    std.os.linux.exit_group(1);
}

pub const panic = std.debug.FullPanic(metroPanic);

// Runtime paths. Defaults match the on-device layout; METROMOD_BASE / METROMOD_MODULES
// environment overrides exist for host-side functional testing.
/// Bumped on every ABI/behavior change; the manager app refuses stale binaries.
const METROLINK_VERSION: u64 = 2;

const DEFAULT_BASE = "/data/adb/metromod";
const DEFAULT_MODULES = "/data/adb/modules";
var base_dir: [:0]const u8 = DEFAULT_BASE;
var manifest_path: [:0]const u8 = DEFAULT_BASE ++ "/manifest.json";
var store_dir: [:0]const u8 = DEFAULT_BASE ++ "/store";
var modules_dir: [:0]const u8 = DEFAULT_MODULES;
const BOOT_SCRIPT = "/data/adb/post-fs-data.d/metromod.sh";
const BOOT_SCRIPT_CONTENT =
    \\#!/system/bin/sh
    \\METROMOD=/data/adb/metromod/metrolink
    \\[ -x "$METROMOD" ] && "$METROMOD" apply >/dev/null 2>&1
    \\
;

const MetroEntry = extern struct {
    id: [160]u8,
    name: [160]u8,
    version: [96]u8,
    version_code: i64,
    enabled: i32,
    pad: i32 = 0,

    fn idSlice(self: *const MetroEntry) []const u8 {
        return cstr(&self.id);
    }

    fn idZ(self: *const MetroEntry) [*:0]const u8 {
        return @ptrCast(&self.id);
    }

    fn toOwned(self: *const MetroEntry, alloc: std.mem.Allocator) !OwnedEntry {
        return .{
            .id = try alloc.dupe(u8, self.idSlice()),
            .enabled = self.enabled != 0,
        };
    }
};

const OwnedEntry = struct {
    id: []const u8,
    enabled: bool,
};

extern fn metroconf_new() u64;
extern fn metroconf_parse(data: [*]const u8, len: usize) u64;
extern fn metroconf_close(handle: u64) void;
extern fn metroconf_serialize(handle: u64, buf: ?[*]u8, cap: usize) i64;
extern fn metroconf_count(handle: u64) i64;
extern fn metroconf_entry(handle: u64, index: i64, out: *MetroEntry) i32;
extern fn metroconf_upsert(handle: u64, entry: *const MetroEntry) i32;
extern fn metroconf_remove(handle: u64, id: [*:0]const u8) i32;
extern fn metroconf_set_enabled(handle: u64, id: [*:0]const u8, enabled: i32) i32;
extern fn metroconf_set_updated(handle: u64, updated_at: i64) i32;
extern fn metroconf_error(buf: [*]u8, cap: usize) i32;

extern fn metro_read_file(path: [*:0]const u8, out: *[*c]u8) c_long;
extern fn metro_write_file_atomic(path: [*:0]const u8, data: [*]const u8, len: usize) c_int;
extern fn metro_copy_tree(src: [*:0]const u8, dst: [*:0]const u8) c_int;
extern fn metro_mkdirs(path: [*:0]const u8) c_int;
extern fn metro_dir_exists(path: [*:0]const u8) c_int;
extern fn metro_file_exists(path: [*:0]const u8) c_int;
extern fn metro_remove_tree(path: [*:0]const u8) c_int;
extern fn metro_unlink(path: [*:0]const u8) c_int;
extern fn metro_touch(path: [*:0]const u8) c_int;
extern fn metro_prop_get(path: [*:0]const u8, key: [*:0]const u8, out: *[*c]u8) c_int;
extern fn metro_list_dir(path: [*:0]const u8, out: *[*c]u8) c_long;
extern fn metro_free(ptr: ?*anyopaque) void;
extern fn metro_now() i64;

/// Minimal stdout plumbing: raw fd 1 writes through the C library-free posix layer.
fn emit(bytes: []const u8) void {
    var off: usize = 0;
    while (off < bytes.len) {
        const n = std.os.linux.write(1, bytes.ptr + off, bytes.len - off);
        if (n == 0) return;
        off += n;
    }
}

fn print(comptime fmt: []const u8, args: anytype) void {
    var buf: [8192]u8 = undefined;
    const text = std.fmt.bufPrint(&buf, fmt, args) catch return;
    emit(text);
}

extern "c" fn getenv(name: [*:0]const u8) ?[*:0]u8;

fn envOrNull(name: [:0]const u8) ?[]const u8 {
    const value = getenv(name.ptr) orelse return null;
    return std.mem.span(value);
}

fn cstr(buf: []const u8) []const u8 {
    const end = std.mem.indexOfScalar(u8, buf, 0) orelse buf.len;
    return buf[0..end];
}

fn fail(comptime fmt: []const u8, args: anytype) noreturn {
    var message_buf: [512]u8 = undefined;
    _ = metroconf_error(&message_buf, message_buf.len);
    const parser_message = cstr(&message_buf);
    print("{{\"ok\":false,\"error\":\"" ++ fmt ++ ":{s}\"}}\n", args ++ .{parser_message});
    std.os.linux.exit_group(1);
}

/// Loads the manifest from disk into a metroconf handle. An absent manifest yields a fresh one.
fn loadManifest(alloc: std.mem.Allocator) !u64 {
    var file_buf: [*c]u8 = null;
    const len = metro_read_file(manifest_path, &file_buf);
    if (len <= 0 or file_buf == null) {
        // Absent or zero-length manifest: start from a fresh declaration.
        const handle = metroconf_new();
        if (handle == 0) fail("cannot create manifest handle", .{});
        return handle;
    }
    defer metro_free(file_buf);
    const handle = metroconf_parse(@ptrCast(file_buf), @intCast(len));
    if (handle == 0) {
        // A corrupt declaration is reported, never silently swallowed.
        fail("manifest is corrupt; remove {s} to reset", .{manifest_path});
    }
    _ = alloc;
    return handle;
}

fn saveManifest(alloc: std.mem.Allocator, handle: u64) !void {
    if (metro_mkdirs(base_dir) != 0) fail("mkdir {s}", .{base_dir});
    _ = metroconf_set_updated(handle, metro_now());

    const needed = metroconf_serialize(handle, null, 0);
    if (needed < 0) fail("serialize failed ({d})", .{needed});
    const len: usize = @intCast(needed);
    const json = try alloc.alloc(u8, len + 1);
    defer alloc.free(json);
    const written = metroconf_serialize(handle, json.ptr, len + 1);
    if (written < 0) fail("serialize failed ({d})", .{written});
    if (metro_write_file_atomic(manifest_path, json.ptr, len) != 0) {
        fail("cannot write {s} (check labels/permissions)", .{manifest_path});
    }
}

fn manifestEntries(alloc: std.mem.Allocator, handle: u64) !std.ArrayList(OwnedEntry) {
    var list = std.ArrayList(OwnedEntry).empty;
    const count = metroconf_count(handle);
    if (count <= 0) return list;
    var i: i64 = 0;
    while (i < count) : (i += 1) {
        var entry: MetroEntry = std.mem.zeroes(MetroEntry);
        if (metroconf_entry(handle, i, &entry) == 0) {
            try list.append(alloc, try entry.toOwned(alloc));
        }
    }
    return list;
}

/// apply — reconcile the live module directory with the manifest.
/// For every enabled entry: restore the module from the store when deleted, and clear the
/// `disable` marker. Disabled entries are left untouched; unlisted modules are never harmed.
fn cmdApply(alloc: std.mem.Allocator) !void {
    const handle = try loadManifest(alloc);
    defer metroconf_close(handle);
    var entries = try manifestEntries(alloc, handle);
    defer entries.deinit(alloc);

    if (metro_mkdirs(store_dir) != 0) fail("mkdir {s}", .{store_dir});

    var restored = std.ArrayList([]const u8).empty;
    defer restored.deinit(alloc);
    var revived = std.ArrayList([]const u8).empty;
    defer revived.deinit(alloc);
    var errors = std.ArrayList([]const u8).empty;
    defer errors.deinit(alloc);

    for (entries.items) |entry| {
        if (!entry.enabled) continue;
        var module_path_buf: [512:0]u8 = undefined;
        var store_path_buf: [512:0]u8 = undefined;
        _ = std.fmt.bufPrintZ(&module_path_buf, "{s}/{s}", .{ modules_dir, entry.id }) catch continue;
        _ = std.fmt.bufPrintZ(&store_path_buf, "{s}/{s}", .{ store_dir, entry.id }) catch continue;

        const module_exists = metro_dir_exists(&module_path_buf) != 0;
        const store_exists = metro_dir_exists(&store_path_buf) != 0;

        if (!module_exists) {
            if (store_exists) {
                if (metro_copy_tree(&store_path_buf, &module_path_buf) == 0) {
                    try restored.append(alloc, entry.id);
                    print("metrolink: restored {s} from store\n", .{entry.id});
                } else {
                    try errors.append(alloc, entry.id);
                    print("metrolink: failed to restore {s}\n", .{entry.id});
                }
            } else {
                try errors.append(alloc, entry.id);
                print("metrolink: no snapshot for {s}\n", .{entry.id});
            }
            continue;
        }

        var disable_path_buf: [512:0]u8 = undefined;
        _ = std.fmt.bufPrintZ(&disable_path_buf, "{s}/{s}/disable", .{ modules_dir, entry.id }) catch continue;
        if (metro_file_exists(&disable_path_buf) != 0) {
            if (metro_unlink(&disable_path_buf) == 0) {
                try revived.append(alloc, entry.id);
                print("metrolink: re-enabled {s}\n", .{entry.id});
            } else {
                try errors.append(alloc, entry.id);
            }
        }
    }

    print("{{\"ok\":true,\"cmd\":\"apply\",\"restored\":[", .{});
    for (restored.items, 0..) |id, i| {
        if (i != 0) print(",", .{});
        print("\"{s}\"", .{id});
    }
    print("],\"reenabled\":[", .{});
    for (revived.items, 0..) |id, i| {
        if (i != 0) print(",", .{});
        print("\"{s}\"", .{id});
    }
    print("],\"errors\":[", .{});
    for (errors.items, 0..) |id, i| {
        if (i != 0) print(",", .{});
        print("\"{s}\"", .{id});
    }
    print("]}}\n", .{});
}

/// snapshot <id> — copy the installed module into the immutable store and pin it.
fn cmdSnapshot(alloc: std.mem.Allocator, id: []const u8) !void {
    var module_path_buf: [512:0]u8 = undefined;
    var store_path_buf: [512:0]u8 = undefined;
    var prop_path_buf: [512:0]u8 = undefined;
    _ = try std.fmt.bufPrintZ(&module_path_buf, "{s}/{s}", .{ modules_dir, id });
    _ = try std.fmt.bufPrintZ(&store_path_buf, "{s}/{s}", .{ store_dir, id });
    _ = try std.fmt.bufPrintZ(&prop_path_buf, "{s}/{s}/module.prop", .{ modules_dir, id });

    if (metro_dir_exists(&module_path_buf) == 0) fail("module {s} not installed", .{id});

    // Refresh the snapshot: remove the old copy, then clone the live directory.
    if (metro_dir_exists(&store_path_buf) != 0 and metro_remove_tree(&store_path_buf) != 0) {
        fail("cannot refresh snapshot for {s}", .{id});
    }
    if (metro_copy_tree(&module_path_buf, &store_path_buf) != 0) {
        fail("snapshot copy failed for {s}", .{id});
    }

    var name_buf: [*c]u8 = null;
    var version_buf: [*c]u8 = null;
    var prop_id_buf: [*c]u8 = null;
    var version_code: i64 = -1;
    defer {
        if (name_buf) |p| metro_free(p);
        if (version_buf) |p| metro_free(p);
        if (prop_id_buf) |p| metro_free(p);
    }
    if (metro_prop_get(&prop_path_buf, "name", &name_buf) == 0 and name_buf != null) {}
    if (metro_prop_get(&prop_path_buf, "version", &version_buf) == 0 and version_buf != null) {}
    if (metro_prop_get(&prop_path_buf, "versionCode", &prop_id_buf) == 0 and prop_id_buf != null) {
        version_code = std.fmt.parseInt(i64, std.mem.span(@as([*:0]const u8, @ptrCast(prop_id_buf))), 10) catch -1;
    }

    const handle = try loadManifest(alloc);
    defer metroconf_close(handle);

    var entry: MetroEntry = std.mem.zeroes(MetroEntry);
    copyEntry(&entry.id, id);
    if (name_buf) |p| copyEntry(&entry.name, std.mem.span(@as([*:0]const u8, @ptrCast(p))));
    if (version_buf) |p| copyEntry(&entry.version, std.mem.span(@as([*:0]const u8, @ptrCast(p))));
    entry.version_code = version_code;
    entry.enabled = 1;
    if (metroconf_upsert(handle, &entry) != 0) fail("upsert {s} failed", .{id});

    try saveManifest(alloc, handle);
    print("{{\"ok\":true,\"cmd\":\"snapshot\",\"id\":\"{s}\"}}\n", .{id});
}

/// unpin <id> — drop the module from the manifest; the store snapshot is kept for safety.
fn cmdUnpin(alloc: std.mem.Allocator, id: []const u8) !void {
    var id_buf: [512:0]u8 = undefined;
    _ = try std.fmt.bufPrintZ(&id_buf, "{s}", .{id});
    const handle = try loadManifest(alloc);
    defer metroconf_close(handle);
    if (metroconf_remove(handle, &id_buf) != 0) fail("{s} is not pinned", .{id});
    try saveManifest(alloc, handle);
    print("{{\"ok\":true,\"cmd\":\"unpin\",\"id\":\"{s}\"}}\n", .{id});
}

/// toggle <id> <0|1> — flip the enforcement flag for one pinned module.
fn cmdToggle(alloc: std.mem.Allocator, id: []const u8, enabled: bool) !void {
    var id_buf: [512:0]u8 = undefined;
    _ = try std.fmt.bufPrintZ(&id_buf, "{s}", .{id});
    const handle = try loadManifest(alloc);
    defer metroconf_close(handle);
    if (metroconf_set_enabled(handle, &id_buf, if (enabled) 1 else 0) != 0) {
        fail("{s} is not pinned", .{id});
    }
    try saveManifest(alloc, handle);
    print("{{\"ok\":true,\"cmd\":\"toggle\",\"id\":\"{s}\",\"enabled\":{}}}\n", .{ id, enabled });
}

/// prune — remove store snapshots whose manifest entry is gone.
fn cmdPrune(alloc: std.mem.Allocator) !void {
    const handle = try loadManifest(alloc);
    defer metroconf_close(handle);
    const entries = try manifestEntries(alloc, handle);

    var names_buf: [*c]u8 = null;
    const names_len = metro_list_dir(store_dir, &names_buf);
    if (names_len <= 0 or names_buf == null) {
        print("{{\"ok\":true,\"cmd\":\"prune\",\"removed\":[]}}\n", .{});
        return;
    }
    defer metro_free(names_buf);

    var removed = std.ArrayList([]const u8).empty;
    defer removed.deinit(alloc);

    // Walk the NUL-separated name block returned by the C shim.
    var cursor: [*c]u8 = names_buf;
    while (cursor[0] != 0) {
        const name = std.mem.span(@as([*:0]const u8, @ptrCast(cursor)));
        cursor += name.len + 1;

        var pinned = false;
        for (entries.items) |entry| {
            if (std.mem.eql(u8, entry.id, name)) {
                pinned = true;
                break;
            }
        }
        if (pinned) continue;
        var store_path_buf: [512:0]u8 = undefined;
        _ = std.fmt.bufPrintZ(&store_path_buf, "{s}/{s}", .{ store_dir, name }) catch continue;
        if (metro_remove_tree(&store_path_buf) == 0) {
            try removed.append(alloc, try alloc.dupe(u8, name));
        }
    }

    print("{{\"ok\":true,\"cmd\":\"prune\",\"removed\":[", .{});
    for (removed.items, 0..) |id, i| {
        if (i != 0) print(",", .{});
        print("\"{s}\"", .{id});
    }
    print("]}}\n", .{});
}

/// list — echo the manifest to stdout for the manager app.
fn cmdList() !void {
    var file_buf: [*c]u8 = null;
    const len = metro_read_file(manifest_path, &file_buf);
    if (len < 0 or file_buf == null) {
        print("{{\"version\":1,\"updated_at\":0,\"modules\":[]}}\n", .{});
        return;
    }
    defer metro_free(file_buf);
    emit(file_buf[0..@intCast(len)]);
    emit("\n");
}

/// install-boot — (re)write the post-fs-data enforcement script.
fn cmdInstallBoot() !void {
    var dir_buf: [512:0]u8 = undefined;
    _ = try std.fmt.bufPrintZ(&dir_buf, "/data/adb/post-fs-data.d", .{});
    if (metro_mkdirs(&dir_buf) != 0) fail("mkdir {s}", .{"/data/adb/post-fs-data.d"});
    if (metro_write_file_atomic(BOOT_SCRIPT, BOOT_SCRIPT_CONTENT, BOOT_SCRIPT_CONTENT.len) != 0) {
        fail("write {s}", .{BOOT_SCRIPT});
    }
    print("{{\"ok\":true,\"cmd\":\"install-boot\"}}\n", .{});
}

fn copyEntry(dest: anytype, src: []const u8) void {
    @memset(dest, 0);
    const take = @min(src.len, dest.len - 1);
    @memcpy(dest[0..take], src[0..take]);
}

/// C ABI entry point so the final Android executable is linked by the NDK; Zig compiles this
/// file to a plain object file and shim.c bridges its main() into metrolink_main.
export fn metrolink_main(argc: c_int, argv: [*][*:0]u8) c_int {
    // Internal errors must never exit silently: the manager app surfaces this JSON verbatim.
    run(@intCast(argc), argv) catch |err| {
        print("{{\"ok\":false,\"error\":\"internal: {s}\"}}\n", .{@errorName(err)});
        return 1;
    };
    return 0;
}

fn run(argc: usize, argv: [*][*:0]u8) !void {
    if (envOrNull("METROMOD_BASE")) |base| {
        base_dir = try std.fmt.allocPrintSentinel(std.heap.c_allocator, "{s}", .{base}, 0);
        manifest_path = try std.fmt.allocPrintSentinel(std.heap.c_allocator, "{s}/manifest.json", .{base}, 0);
        store_dir = try std.fmt.allocPrintSentinel(std.heap.c_allocator, "{s}/store", .{base}, 0);
    }
    if (envOrNull("METROMOD_MODULES")) |mods| {
        modules_dir = try std.fmt.allocPrintSentinel(std.heap.c_allocator, "{s}", .{mods}, 0);
    }

    var arena = std.heap.ArenaAllocator.init(std.heap.c_allocator);
    defer arena.deinit();
    const alloc = arena.allocator();

    const cmd: []const u8 = if (argc > 1) std.mem.span(argv[1]) else "apply";
    const arg1: ?[]const u8 = if (argc > 2) std.mem.span(argv[2]) else null;
    const arg2: ?[]const u8 = if (argc > 3) std.mem.span(argv[3]) else null;

    if (std.mem.eql(u8, cmd, "apply")) {
        try cmdApply(alloc);
    } else if (std.mem.eql(u8, cmd, "list")) {
        try cmdList();
    } else if (std.mem.eql(u8, cmd, "snapshot")) {
        try cmdSnapshot(alloc, arg1 orelse fail("snapshot needs a module id", .{}));
    } else if (std.mem.eql(u8, cmd, "unpin")) {
        try cmdUnpin(alloc, arg1 orelse fail("unpin needs a module id", .{}));
    } else if (std.mem.eql(u8, cmd, "toggle")) {
        const id = arg1 orelse fail("toggle needs a module id", .{});
        const state = arg2 orelse fail("toggle needs 0 or 1", .{});
        try cmdToggle(alloc, id, std.mem.eql(u8, state, "1"));
    } else if (std.mem.eql(u8, cmd, "prune")) {
        try cmdPrune(alloc);
    } else if (std.mem.eql(u8, cmd, "install-boot")) {
        try cmdInstallBoot();
    } else if (std.mem.eql(u8, cmd, "version")) {
        print("{{\"ok\":true,\"version\":{d}}}", .{METROLINK_VERSION});
        emit("\n");
    } else {
        fail("unknown command {s}", .{cmd});
    }
}
