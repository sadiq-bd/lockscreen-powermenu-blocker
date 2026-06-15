#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <linux/input.h>
#include <sys/ioctl.h>
#include <sys/poll.h>
#include <time.h>
#include <stdbool.h>
#include <errno.h>

#define LOG_FILE "/data/local/tmp/lockscreen-powermenu-blocker.log"
#define TARGET_DURATION_MS 210

// Helper to get monotonic time in milliseconds
long long get_now_ms() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long long)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

// Scans /dev/input/event* to find the device matching KEY_POWER
int find_power_key_device(char *out_path, size_t max_len) {
    char path[64];
    // Scan up to 32 event nodes
    for (int i = 0; i < 32; i++) {
        snprintf(path, sizeof(path), "/dev/input/event%d", i);
        int fd = open(path, O_RDONLY | O_NONBLOCK);
        if (fd < 0) continue;

        uint8_t key_bitmask[KEY_MAX / 8 + 1];
        memset(key_bitmask, 0, sizeof(key_bitmask));

        // Query device capabilities
        if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(key_bitmask)), key_bitmask) >= 0) {
            int byte_index = KEY_POWER / 8;
            int bit_index = KEY_POWER % 8;
            if (key_bitmask[byte_index] & (1 << bit_index)) {
                strncpy(out_path, path, max_len);
                close(fd);
                return 0; // Device found
            }
        }
        close(fd);
    }
    return -1;
}

// Replicates the `dumpsys window | grep ...` lockscreen check
bool is_device_locked() {
    FILE *fp = popen("dumpsys window", "r");
    if (!fp) return false;

    char buffer[512];
    bool locked = false;
    while (fgets(buffer, sizeof(buffer), fp) != NULL) {
        if (strstr(buffer, "mDreamingLockscreen=true") != NULL) {
            locked = true;
            break;
        }
    }
    pclose(fp);
    return locked;
}

// Replicates `input keyevent 26`
void trigger_power_press() {
    system("input keyevent 26");
}

int main() {
    // Redirect stdout and stderr to the log file (simulating `exec >> LOG_FILE 2>&1`)
    freopen(LOG_FILE, "a", stdout);
    freopen(LOG_FILE, "a", stderr);
    setvbuf(stdout, NULL, _IOLBF, 0); // Enable line buffering

    char dev_path[64];
    if (find_power_key_device(dev_path, sizeof(dev_path)) != 0) {
        fprintf(stderr, "[%lld] Error: Power key device not found.\n", get_now_ms());
        return 1;
    }
    printf("[%lld] Monitoring power key on: %s\n", get_now_ms(), dev_path);

    int fd = open(dev_path, O_RDONLY | O_NONBLOCK);
    if (fd < 0) {
        perror("Failed to open input device");
        return 1;
    }

    struct pollfd fds[1];
    fds[0].fd = fd;
    fds[0].events = POLLIN;

    bool is_down = false;
    long long down_time = 0;

    while (true) {
        // Poll for events with a 50ms timeout (replaces `sleep 0.05`)
        int ret = poll(fds, 1, 50);

        if (ret > 0 && (fds[0].revents & POLLIN)) {
            struct input_event ev;
            // Drain all available events from the non-blocking descriptor
            while (read(fd, &ev, sizeof(ev)) == sizeof(ev)) {
                if (ev.type == EV_KEY && ev.code == KEY_POWER) {
                    if (ev.value == 1) {       // KEY_POWER DOWN
                        is_down = true;
                        down_time = get_now_ms();
                    } else if (ev.value == 0) { // KEY_POWER UP
                        is_down = false;
                    }
                }
            }
        }

        // Evaluate key hold duration
        if (is_down) {
            long long now = get_now_ms();
            if ((now - down_time) >= TARGET_DURATION_MS) {
                if (is_device_locked()) {
                    printf("[%lld] Blocker triggered (Held for %lld ms). Intercepting power menu...\n", now, (now - down_time));
                    trigger_power_press();
                    is_down = false; // Reset state to prevent immediate re-triggering during the same hold
                }
            }
        }
    }

    close(fd);
    return 0;
}
