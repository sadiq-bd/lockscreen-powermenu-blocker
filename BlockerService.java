import android.content.ContentResolver;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.KeyguardManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.lang.reflect.Method;

public class BlockerService {
    private static final long SAFETY_REFRESH_MS = 5000;
    private static final int USER_SYSTEM = 0;
    private static final String NULL_SENTINEL = "__LOCKSCREEN_POWERMENU_BLOCKER_NULL__";
    private static final String BACKUP_PREFIX = "lockscreen_powermenu_blocker_backup_";

    // AOSP PhoneWindowManager reads these Settings.Global keys for power hold actions.
    private static final String POWER_BUTTON_LONG_PRESS = "power_button_long_press";
    private static final String POWER_BUTTON_VERY_LONG_PRESS = "power_button_very_long_press";
    private static final String LEGACY_LONG_PRESS_POWER_BEHAVIOR = "long_press_power_behavior";

    private static final int POWER_BEHAVIOR_NOTHING = 0;

    private static final String[] POWER_MENU_SETTINGS = new String[] {
            POWER_BUTTON_LONG_PRESS,
            POWER_BUTTON_VERY_LONG_PRESS,
            LEGACY_LONG_PRESS_POWER_BEHAVIOR
    };

    private static final Object policyLock = new Object();

    private static Context systemContext = null;
    private static Object windowManagerService = null;
    private static ContentResolver contentResolver = null;
    private static KeyguardManager keyguardManager = null;
    private static Handler handler = null;
    private static Method isKeyguardLockedMethod = null;
    private static boolean powerMenuBlocked = false;

    public static void main(String[] args) {
        System.out.println("[BlockerService] Initializing internal power-menu policy daemon...");

        initSystemServices();
        registerStateReceivers();
        applyPowerMenuPolicy("startup");
        scheduleSafetyRefresh();

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        Looper.loop();
    }

    private static void initSystemServices() {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method getService = sm.getMethod("getService", String.class);

            // Bind WindowManager to check lock state
            IBinder wmBinder = (IBinder) getService.invoke(null, "window");
            Class<?> wmStub = Class.forName("android.view.IWindowManager$Stub");
            windowManagerService = wmStub.getMethod("asInterface", IBinder.class).invoke(null, wmBinder);
            isKeyguardLockedMethod = windowManagerService.getClass().getMethod("isKeyguardLocked");

            systemContext = createSystemContext();
            contentResolver = systemContext.getContentResolver();
            keyguardManager = (KeyguardManager) systemContext.getSystemService(Context.KEYGUARD_SERVICE);
            if (Looper.myLooper() == null) {
                Looper.prepare();
            }
            handler = new Handler(Looper.myLooper());

            System.out.println("[BlockerService] Connected to WindowManager and SettingsProvider.");
        } catch (Exception e) {
            System.err.println("[BlockerService] Failed to initialize internal services:");
            e.printStackTrace();
        }
    }

    private static Context createSystemContext() throws Exception {
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
        if (activityThread == null) {
            activityThread = activityThreadClass.getMethod("systemMain").invoke(null);
        }
        return (Context) activityThreadClass.getMethod("getSystemContext").invoke(activityThread);
    }

    private static void registerStateReceivers() {
        if (systemContext == null) {
            System.err.println("[BlockerService] Cannot register state receivers: no system context.");
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);

        systemContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent != null ? intent.getAction() : "unknown";
                applyPowerMenuPolicy(action);
            }
        }, filter);

        System.out.println("[BlockerService] Registered lockscreen state receivers.");
    }

    private static void scheduleSafetyRefresh() {
        if (handler == null) return;

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                applyPowerMenuPolicy("safety-refresh");
                scheduleSafetyRefresh();
            }
        }, SAFETY_REFRESH_MS);
    }

    private static void applyPowerMenuPolicy(String reason) {
        synchronized (policyLock) {
            boolean locked = isDeviceLocked();

            if (locked) {
                backupAndDisablePowerMenu(reason);
            } else {
                restorePowerMenu(reason);
            }
        }
    }

    private static boolean isDeviceLocked() {
        boolean locked = false;

        if (windowManagerService != null && isKeyguardLockedMethod != null) {
            try {
                locked = (boolean) isKeyguardLockedMethod.invoke(windowManagerService);
            } catch (Exception e) {
                System.err.println("[BlockerService] isKeyguardLocked failed: " + e.getMessage());
            }
        }

        if (!locked && keyguardManager != null) {
            try {
                locked = keyguardManager.isDeviceLocked();
            } catch (Exception e) {
                System.err.println("[BlockerService] isDeviceLocked failed: " + e.getMessage());
            }
        }

        return locked;
    }

    private static void backupAndDisablePowerMenu(String reason) {
        boolean changed = false;

        for (String key : POWER_MENU_SETTINGS) {
            String backupKey = getBackupKey(key);
            String backup = getGlobalSetting(backupKey);

            if (backup == null) {
                putGlobalSetting(backupKey, encodeNullable(getGlobalSetting(key)));
            }

            String disabled = String.valueOf(POWER_BEHAVIOR_NOTHING);
            String current = getGlobalSetting(key);
            if (!disabled.equals(current)) {
                putGlobalSetting(key, disabled);
                changed = true;
            }
        }

        if (!powerMenuBlocked || changed) {
            powerMenuBlocked = true;
            System.out.println("[BlockerService] Power menu disabled while device is locked. reason=" + reason);
        }
    }

    private static void restorePowerMenu(String reason) {
        boolean restored = false;

        for (String key : POWER_MENU_SETTINGS) {
            String backupKey = getBackupKey(key);
            String backup = getGlobalSetting(backupKey);

            if (backup != null) {
                putGlobalSetting(key, decodeNullable(backup));
                putGlobalSetting(backupKey, null);
                restored = true;
            }
        }

        if (powerMenuBlocked || restored) {
            powerMenuBlocked = false;
            System.out.println("[BlockerService] Power menu settings restored after unlock. reason=" + reason);
        }
    }

    private static String getBackupKey(String key) {
        return BACKUP_PREFIX + key;
    }

    private static String encodeNullable(String value) {
        return value == null ? NULL_SENTINEL : value;
    }

    private static String decodeNullable(String value) {
        return NULL_SENTINEL.equals(value) ? null : value;
    }

    private static String getGlobalSetting(String key) {
        if (contentResolver == null) return null;
        try {
            Class<?> settingsGlobalClass = Class.forName("android.provider.Settings$Global");
            Method getString = settingsGlobalClass.getMethod("getStringForUser",
                    Class.forName("android.content.ContentResolver"), String.class, Integer.TYPE);
            return (String) getString.invoke(null, contentResolver, key, USER_SYSTEM);
        } catch (Exception e) {
            System.err.println("[BlockerService] Failed to read global setting " + key + ": " + e.getMessage());
            return null;
        }
    }

    private static void putGlobalSetting(String key, String value) {
        if (contentResolver == null) {
            System.err.println("[BlockerService] Cannot mutate global setting " + key + ": no ContentResolver.");
            return;
        }
        try {
            Class<?> settingsGlobalClass = Class.forName("android.provider.Settings$Global");
            Method putString = settingsGlobalClass.getMethod("putStringForUser",
                    Class.forName("android.content.ContentResolver"), String.class, String.class, Integer.TYPE);
            boolean ok = (boolean) putString.invoke(null, contentResolver, key, value, USER_SYSTEM);
            if (!ok) {
                System.err.println("[BlockerService] Framework rejected global setting mutation: " + key);
            }
        } catch (Exception e) {
            System.err.println("[BlockerService] Failed to mutate global setting " + key + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
