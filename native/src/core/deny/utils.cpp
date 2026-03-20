#include <sys/types.h>
#include <sys/stat.h>
#include <sys/inotify.h>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <set>
#include <map>

#include <consts.hpp>
#include <sqlite.hpp>
#include <core.hpp>

#include "deny.hpp"

using namespace std;

// For the following data structures:
// If package name == ISOLATED_MAGIC, or app ID == -1, it means isolated service

// Package name -> list of process names
static unique_ptr<map<string, set<string, StringCmp>, StringCmp>> pkg_to_procs_;
#define pkg_to_procs (*pkg_to_procs_)

// app ID -> list of pkg names (string_view points to a pkg_to_procs key)
static unique_ptr<map<int, set<string_view>>> app_id_to_pkgs_;
#define app_id_to_pkgs (*app_id_to_pkgs_)

// Locks the data structures above
static pthread_mutex_t data_lock = PTHREAD_MUTEX_INITIALIZER;

atomic<bool> denylist_enforced = false;
atomic<bool> enforce_sulist = false;

static int get_app_id(const vector<int> &users, const string &pkg) {
    struct stat st{};
    char buf[PATH_MAX];
    for (const auto &user_id: users) {
        ssprintf(buf, sizeof(buf), "%s/%d/%s", APP_DATA_DIR, user_id, pkg.data());
        if (stat(buf, &st) == 0) {
            return to_app_id(st.st_uid);
        }
    }
    return 0;
}

static void collect_users(vector<int> &users) {
    auto data_dir = xopen_dir(APP_DATA_DIR);
    if (!data_dir)
        return;
    dirent *entry;
    while ((entry = xreaddir(data_dir.get()))) {
        users.emplace_back(parse_int(entry->d_name));
    }
}

static int get_app_id(const string &pkg) {
    if (pkg == ISOLATED_MAGIC)
        return -1;
    vector<int> users;
    collect_users(users);
    return get_app_id(users, pkg);
}

static void update_app_id(int app_id, const string &pkg, bool remove) {
    if (app_id <= 0)
        return;
    if (remove) {
        if (auto it = app_id_to_pkgs.find(app_id); it != app_id_to_pkgs.end()) {
            it->second.erase(pkg);
            if (it->second.empty()) {
                app_id_to_pkgs.erase(it);
            }
        }
    } else {
        app_id_to_pkgs[app_id].emplace(pkg);
    }
}

// Leave /proc fd opened as we're going to read from it repeatedly
static DIR *procfp;

template<class F>
static void crawl_procfs(const F &fn) {
    rewinddir(procfp);
    dirent *dp;
    int pid;
    while ((dp = readdir(procfp))) {
        pid = parse_int(dp->d_name);
        if (pid > 0 && !fn(pid))
            break;
    }
}

static bool str_eql(string_view a, string_view b) { return a == b; }
static bool str_starts_with(string_view a, string_view b) { return a.size() >= b.size() && a.substr(0, b.size()) == b; }

template<bool str_op(string_view, string_view) = str_eql>
static bool proc_name_match(int pid, string_view name) {
    char buf[4019];
    sprintf(buf, "/proc/%d/cmdline", pid);
    if (auto fp = open_file(buf, "re")) {
        fgets(buf, sizeof(buf), fp.get());
        if (str_op(buf, name)) {
            return true;
        }
    }
    return false;
}

bool proc_context_match(int pid, string_view context) {
    char buf[PATH_MAX];
    char con[1024] = {0};

    sprintf(buf, "/proc/%d", pid);
    if (lgetfilecon(buf, byte_data{ con, sizeof(con) })) {
        string_view con_view(con);
        return con_view.size() >= context.size() && con_view.substr(0, context.size()) == context;
    }
    return false;
}

template<bool matcher(int, string_view) = &proc_name_match>
static void kill_process(const char *name, bool multi = false) {
    crawl_procfs([=](int pid) -> bool {
        if (matcher(pid, name)) {
            kill(pid, SIGKILL);
            LOGD("denylist: kill PID=[%d] (%s)\n", pid, name);
            return multi;
        }
        return true;
    });
}

static bool validate(const char *pkg, const char *proc) {
    bool pkg_valid = false;
    bool proc_valid = true;

    if (str_eql(pkg, ISOLATED_MAGIC)) {
        pkg_valid = true;
        for (char c; (c = *proc); ++proc) {
            if (isalnum(c) || c == '_' || c == '.')
                continue;
            if (c == ':')
                break;
            proc_valid = false;
            break;
        }
    } else {
        for (char c; (c = *pkg); ++pkg) {
            if (isalnum(c) || c == '_')
                continue;
            if (c == '.') {
                pkg_valid = true;
                continue;
            }
            pkg_valid = false;
            break;
        }

        for (char c; (c = *proc); ++proc) {
            if (isalnum(c) || c == '_' || c == ':' || c == '.')
                continue;
            proc_valid = false;
            break;
        }
    }
    return pkg_valid && proc_valid;
}

static bool add_hide_set(const char *pkg, const char *proc) {
    auto p = pkg_to_procs[pkg].emplace(proc);
    if (!p.second)
        return false;
    LOGI("denylist add: [%s/%s]\n", pkg, proc);
    if (!denylist_enforced)
        return true;
    if (str_eql(pkg, ISOLATED_MAGIC)) {
        // Kill all matching isolated processes
        kill_process<&proc_name_match<str_starts_with>>(proc, true);
    } else {
        kill_process(proc);
    }
    return true;
}

void scan_deny_apps() {
    if (!app_id_to_pkgs_)
        return;

    app_id_to_pkgs.clear();

    char sql[4096];
    vector<int> users;
    collect_users(users);
    for (auto it = pkg_to_procs.begin(); it != pkg_to_procs.end();) {
        if (it->first == ISOLATED_MAGIC) {
            it++;
            continue;
        }
        int app_id = get_app_id(users, it->first);
        if (app_id == 0) {
            LOGI("denylist rm: [%s]\n", it->first.data());
            ssprintf(sql, sizeof(sql), "DELETE FROM denylist WHERE package_name='%s'",
                     it->first.data());
            db_exec(sql);
            it = pkg_to_procs.erase(it);
        } else {
            update_app_id(app_id, it->first, false);
            it++;
        }
    }
}

static void clear_data() {
    pkg_to_procs_.reset(nullptr);
    app_id_to_pkgs_.reset(nullptr);
}

static bool ensure_data() {
    if (pkg_to_procs_)
        return true;

    LOGI("denylist: initializing internal data structures\n");

    default_new(pkg_to_procs_);
    bool res = db_exec("SELECT * FROM denylist", {}, [](StringSlice columns, const DbValues &values) {
        const char *package_name;
        const char *process;
        for (int i = 0; i < columns.size(); ++i) {
            const auto &name = columns[i];
            if (name == "package_name") {
                package_name = values.get_text(i);
            } else if (name == "process") {
                process = values.get_text(i);
            }
        }
        add_hide_set(package_name, process);
    });
    if (!res)
        goto error;

    default_new(app_id_to_pkgs_);
    scan_deny_apps();

    return true;

error:
    clear_data();
    return false;
}

static int add_list(const char *pkg, const char *proc) {
    if (proc[0] == '\0')
        proc = pkg;

    if (!validate(pkg, proc))
        return DenyResponse::INVALID_PKG;

    {
        mutex_guard lock(data_lock);
        if (!ensure_data())
            return DenyResponse::ERROR;
        int app_id = get_app_id(pkg);
        if (app_id == 0)
            return DenyResponse::INVALID_PKG;
        if (!add_hide_set(pkg, proc))
            return DenyResponse::ITEM_EXIST;
        auto it = pkg_to_procs.find(pkg);
        update_app_id(app_id, it->first, false);
    }

    // Add to database
    char sql[4096];
    ssprintf(sql, sizeof(sql),
            "INSERT INTO denylist (package_name, process) VALUES('%s', '%s')", pkg, proc);
    return db_exec(sql) ? DenyResponse::OK : DenyResponse::ERROR;
}

int add_list(int client) {
    string pkg = read_string(client);
    string proc = read_string(client);
    return add_list(pkg.data(), proc.data());
}

static int rm_list(const char *pkg, const char *proc) {
    {
        mutex_guard lock(data_lock);
        if (!ensure_data())
            return DenyResponse::ERROR;

        bool remove = false;

        auto it = pkg_to_procs.find(pkg);
        if (it != pkg_to_procs.end()) {
            if (proc[0] == '\0') {
                update_app_id(get_app_id(pkg), it->first, true);
                pkg_to_procs.erase(it);
                remove = true;
                LOGI("denylist rm: [%s]\n", pkg);
            } else if (it->second.erase(proc) != 0) {
                remove = true;
                LOGI("denylist rm: [%s/%s]\n", pkg, proc);
                if (it->second.empty()) {
                    update_app_id(get_app_id(pkg), it->first, true);
                    pkg_to_procs.erase(it);
                }
            }
        }

        if (!remove)
            return DenyResponse::ITEM_NOT_EXIST;
    }

    char sql[4096];
    if (proc[0] == '\0')
        ssprintf(sql, sizeof(sql), "DELETE FROM denylist WHERE package_name='%s'", pkg);
    else
        ssprintf(sql, sizeof(sql),
                "DELETE FROM denylist WHERE package_name='%s' AND process='%s'", pkg, proc);
    return db_exec(sql) ? DenyResponse::OK : DenyResponse::ERROR;
}

int rm_list(int client) {
    string pkg = read_string(client);
    string proc = read_string(client);
    return rm_list(pkg.data(), proc.data());
}

void ls_list(int client) {
    {
        mutex_guard lock(data_lock);
        if (!ensure_data()) {
            write_int(client, static_cast<int>(DenyResponse::ERROR));
            return;
        }

        scan_deny_apps();
        write_int(client,static_cast<int>(DenyResponse::OK));

        for (const auto &[pkg, procs] : pkg_to_procs) {
            for (const auto &proc : procs) {
                write_int(client, pkg.size() + proc.size() + 1);
                xwrite(client, pkg.data(), pkg.size());
                xwrite(client, "|", 1);
                xwrite(client, proc.data(), proc.size());
            }
        }
    }
    write_int(client, 0);
    close(client);
}

int enable_deny() {
    if (denylist_enforced) {
        return DenyResponse::OK;
    } else {
        // SuList and DenyList are mutually exclusive
        if (enforce_sulist) {
            LOGW("SuList mode is enabled, cannot enable DenyList\n");
            return DenyResponse::ERROR;
        }
        mutex_guard lock(data_lock);

        if (access("/proc/self/ns/mnt", F_OK) != 0) {
            LOGW("The kernel does not support mount namespace\n");
            return DenyResponse::NO_NS;
        }

        if (procfp == nullptr && (procfp = opendir("/proc")) == nullptr)
            return DenyResponse::ERROR;

        LOGI("* Enable DenyList\n");

        if (!ensure_data())
            return DenyResponse::ERROR;

        denylist_enforced = true;

        if (!MagiskD::Get().zygisk_enabled()) {
            if (new_daemon_thread(&logcat)) {
                denylist_enforced = false;
                return DenyResponse::ERROR;
            }
        }

        // On Android Q+, also kill blastula pool and all app zygotes
        if (SDK_INT >= 29) {
            kill_process("usap32", true);
            kill_process("usap64", true);
            kill_process<&proc_context_match>("u:r:app_zygote:s0", true);
        }
    }

    MagiskD::Get().set_db_setting(DbEntryKey::DenylistConfig, true);
    return DenyResponse::OK;
}

int disable_deny() {
    if (denylist_enforced.exchange(false)) {
        LOGI("* Disable DenyList\n");
    }
    MagiskD::Get().set_db_setting(DbEntryKey::DenylistConfig, false);
    return DenyResponse::OK;
}

int enable_sulist() {
    if (enforce_sulist) {
        return DenyResponse::OK;
    } else {
        // SuList and DenyList are mutually exclusive
        if (denylist_enforced) {
            disable_deny();
        }
        
        mutex_guard lock(data_lock);

        if (access("/proc/self/ns/mnt", F_OK) != 0) {
            LOGW("The kernel does not support mount namespace\n");
            return DenyResponse::NO_NS;
        }

        if (procfp == nullptr && (procfp = opendir("/proc")) == nullptr)
            return DenyResponse::ERROR;

        LOGI("* Enable SuList (Enforce Whitelist Mode)\n");

        if (!ensure_data())
            return DenyResponse::ERROR;

        enforce_sulist = true;

        if (!MagiskD::Get().zygisk_enabled()) {
            if (new_daemon_thread(&logcat)) {
                enforce_sulist = false;
                return DenyResponse::ERROR;
            }
        }

        // On Android Q+, also kill blastula pool and all app zygotes
        // This forces apps to restart with the new SuList enforcement
        if (SDK_INT >= 29) {
            kill_process("usap32", true);
            kill_process("usap64", true);
            kill_process<&proc_context_match>("u:r:app_zygote:s0", true);
        }
        
        // Kill all user apps to force re-execution with SuList
        // Apps not in SuList will have Magisk hidden after restart
        crawl_procfs([](int pid) -> bool {
            char buf[PATH_MAX];
            sprintf(buf, "/proc/%d/cmdline", pid);
            if (auto fp = open_file(buf, "re")) {
                fgets(buf, sizeof(buf), fp.get());
                string_view cmd(buf);
                // Skip system processes and magiskd
                if (cmd.empty() || cmd == "magiskd" || cmd.starts_with("/system/"))
                    return true;
                // Kill user apps
                if (cmd.contains(".") && !cmd.starts_with("android.")) {
                    kill(pid, SIGKILL);
                    LOGD("SuList: kill PID=[%d] (%s)\n", pid, cmd.data());
                }
            }
            return true;
        });
    }

    MagiskD::Get().set_db_setting(DbEntryKey::SulistConfig, true);
    return DenyResponse::OK;
}

int disable_sulist() {
    if (enforce_sulist.exchange(false)) LOGI("* Disable SuList\n");
    MagiskD::Get().set_db_setting(DbEntryKey::SulistConfig, false);
    return DenyResponse::OK;
}

void initialize_denylist() {
    // Initialize SuList from database
    if (!enforce_sulist) {
        if (MagiskD::Get().get_db_setting(DbEntryKey::SulistConfig)) {
            LOGI("* Initialize SuList from database\n");
            enforce_sulist = true;
        }
    }
    
    // Initialize DenyList from database (only if SuList is not enabled)
    if (!denylist_enforced && !enforce_sulist) {
        if (MagiskD::Get().get_db_setting(DbEntryKey::DenylistConfig))
            enable_deny();
    }
}

bool is_deny_target(int uid, string_view process) {
    mutex_guard lock(data_lock);
    if (!ensure_data())
        return false;

    int app_id = to_app_id(uid);
    
    // Never hide system_server (uid 1000)
    if (uid == 1000) {
        return false;
    }
    
    // Never hide system apps (uid < 10000)
    if (app_id < 10000) {
        return false;
    }
    
    // SuList mode: invert logic - hide everything NOT in the list
    // In SuList mode, the list contains apps that should SEE Magisk (whitelist)
    // So we hide apps NOT in the list, regardless of root access
    if (enforce_sulist) {
        if (app_id >= 90000) {
            // Isolated process: check if parent app is in SuList
            if (auto it = pkg_to_procs.find(ISOLATED_MAGIC); it != pkg_to_procs.end()) {
                for (const auto &s : it->second) {
                    if (process.size() >= s.size() && process.substr(0, s.size()) == s)
                        return false; // In SuList, do NOT hide
                }
            }
            return true; // Not in SuList, hide
        }
        // Regular app: return true if NOT in list (should hide)
        // DO NOT check uid_granted_root in SuList mode - whitelist takes precedence
        bool in_sulist = app_id_to_pkgs.find(app_id) != app_id_to_pkgs.end();
        LOGD("denylist: SuList mode - [%s] app_id=%d in_sulist=%d\n", process.data(), app_id, in_sulist);
        return !in_sulist; // Hide if NOT in SuList
    }

    // Original DenyList mode logic
    // In DenyList mode, check if app has root access - if so, don't hide
    // This allows apps with root to also see Magisk for proper functionality
    if (MagiskD::Get().uid_granted_root(uid)) {
        LOGD("denylist: [%s] has root access, NOT hiding\n", process.data());
        return false;
    }
    
    if (app_id >= 90000) {
        if (auto it = pkg_to_procs.find(ISOLATED_MAGIC); it != pkg_to_procs.end()) {
            for (const auto &s : it->second) {
                if (process.size() >= s.size() && process.substr(0, s.size()) == s)
                    return true;
            }
        }
        return false;
    } else {
        auto it = app_id_to_pkgs.find(app_id);
        if (it == app_id_to_pkgs.end())
            return false;
        for (const auto &pkg : it->second) {
            if (pkg_to_procs.find(pkg)->second.count(process))
                return true;
        }
    }
    return false;
}

void update_deny_flags(int uid, rust::Str process, uint32_t &flags) {
    // Set mode flags
    if (denylist_enforced) {
        flags |= +ZygiskStateFlags::DenyListEnforced;
    }
    if (enforce_sulist) {
        flags |= +ZygiskStateFlags::SuListEnforced;
    }
    
    // Check if this process should be hidden
    // Convert rust::Str to std::string_view
    std::string_view process_sv(process.data(), process.size());
    if (is_deny_target(uid, process_sv)) {
        flags |= +ZygiskStateFlags::ProcessOnDenyList;
    }
}
