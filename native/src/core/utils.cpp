#include <csignal>
#include <libgen.h>
#include <sys/mount.h>
#include <sys/sysmacros.h>
#include <linux/input.h>
#include <map>

#include <consts.hpp>
#include <base.hpp>
#include <core.hpp>

using namespace std;

bool read_string(int fd, std::string &str) {
    str.clear();
    int len = read_int(fd);
    str.resize(len);
    return xxread(fd, str.data(), len) == len;
}

string read_string(int fd) {
    string str;
    read_string(fd, str);
    return str;
}

void write_string(int fd, string_view str) {
    if (fd < 0) return;
    write_int(fd, str.size());
    xwrite(fd, str.data(), str.size());
}

const char *get_magisk_tmp() {
    static const char *path = nullptr;
    if (path == nullptr) {
        // Try standard locations first
        if (access("/debug_ramdisk/" INTLROOT, F_OK) == 0) {
            path = "/debug_ramdisk";
            LOGD("get_magisk_tmp: found at %s\n", path);
        } else if (access("/sbin/" INTLROOT, F_OK) == 0) {
            path = "/sbin";
            LOGD("get_magisk_tmp: found at %s\n", path);
        } else {
            // Try alternative locations
            LOGD("get_magisk_tmp: standard locations not found, trying alternatives...\n");
            
            // Check if /sbin exists but .magisk is missing
            if (access("/sbin", F_OK) == 0) {
                LOGD("get_magisk_tmp: /sbin exists but /sbin/.magisk not found\n");
            }
            
            // Check if /debug_ramdisk exists but .magisk is missing
            if (access("/debug_ramdisk", F_OK) == 0) {
                LOGD("get_magisk_tmp: /debug_ramdisk exists but /debug_ramdisk/.magisk not found\n");
            }
            
            // Try to find any mounted magisk tmpfs
            FILE *fp = fopen("/proc/mounts", "re");
            if (fp) {
                char line[4096];
                static char found_path[PATH_MAX] = "";
                while (fgets(line, sizeof(line), fp)) {
                    // Look for tmpfs mounts that might be magisk
                    if (strstr(line, "tmpfs") && strstr(line, INTLROOT)) {
                        // Found a tmpfs with .magisk, try to extract the mount point
                        char *start = strchr(line, ' ');
                        if (start) {
                            start++;
                            char *end = strchr(start, ' ');
                            if (end) {
                                *end = '\0';
                                // Check if this path has .magisk
                                char check_path[PATH_MAX];
                                ssprintf(check_path, sizeof(check_path), "%s/" INTLROOT, start);
                                if (access(check_path, F_OK) == 0) {
                                    // Found it! Save to static buffer
                                    LOGD("get_magisk_tmp: found magisk tmpfs mounted at %s\n", start);
                                    strscpy(found_path, start, sizeof(found_path));
                                    fclose(fp);
                                    path = found_path;
                                    return path;
                                }
                            }
                        }
                    }
                }
                fclose(fp);
            }
            
            // Last resort: check if we can find magisk binary somewhere
            if (access("/sbin/magisk", F_OK) == 0) {
                LOGD("get_magisk_tmp: magisk binary found at /sbin, using /sbin\n");
                path = "/sbin";
            } else if (access("/debug_ramdisk/magisk", F_OK) == 0) {
                LOGD("get_magisk_tmp: magisk binary found at /debug_ramdisk, using /debug_ramdisk\n");
                path = "/debug_ramdisk";
            } else {
                LOGE("get_magisk_tmp: no magisk tmp found!\n");
                path = "";
            }
        }
    }
    return path;
}

void unlock_blocks() {
    int fd, dev, OFF = 0;

    auto dir = xopen_dir("/dev/block");
    if (!dir)
        return;
    dev = dirfd(dir.get());

    for (dirent *entry; (entry = readdir(dir.get()));) {
        if (entry->d_type == DT_BLK) {
            if ((fd = openat(dev, entry->d_name, O_RDONLY | O_CLOEXEC)) < 0)
                continue;
            if (ioctl(fd, BLKROSET, &OFF) < 0)
                PLOGE("unlock %s", entry->d_name);
            close(fd);
        }
    }
}

#define test_bit(bit, array) (array[bit / 8] & (1 << (bit % 8)))

bool check_key_combo() {
    uint8_t bitmask[(KEY_MAX + 1) / 8];
    vector<owned_fd> events;
    constexpr char name[] = "/dev/.ev";

    // First collect candidate events that accepts volume down
    for (int minor = 64; minor < 96; ++minor) {
        if (xmknod(name, S_IFCHR | 0444, makedev(13, minor)))
            continue;
        int fd = open(name, O_RDONLY | O_CLOEXEC);
        unlink(name);
        if (fd < 0)
            continue;
        memset(bitmask, 0, sizeof(bitmask));
        ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(bitmask)), bitmask);
        if (test_bit(KEY_VOLUMEDOWN, bitmask))
            events.emplace_back(fd);
        else
            close(fd);
    }
    if (events.empty())
        return false;

    // Check if volume down key is held continuously for more than 3 seconds
    for (int i = 0; i < 300; ++i) {
        bool pressed = false;
        for (int fd : events) {
            memset(bitmask, 0, sizeof(bitmask));
            ioctl(fd, EVIOCGKEY(sizeof(bitmask)), bitmask);
            if (test_bit(KEY_VOLUMEDOWN, bitmask)) {
                pressed = true;
                break;
            }
        }
        if (!pressed)
            return false;
        // Check every 10ms
        usleep(10000);
    }
    LOGD("KEY_VOLUMEDOWN detected: enter safe mode\n");
    return true;
}
