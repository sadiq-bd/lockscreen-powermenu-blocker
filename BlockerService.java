import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

public class BlockerService {
    private static Object windowManagerService = null;
    private static Method isKeyguardLockedMethod = null;

    private static Object inputManagerService = null;
    private static Method injectInputEventMethod = null;

    private static volatile boolean isPowerHeld = false;

    public static void main(String[] args) {
        System.out.println("[BlockerService] Initializing Kernel-Level Hardware Monitor...");

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        // Bind only the proven frameworks from your logs
        initProvenServices();

        // Start the indestructible kernel stream reader
        new Thread(BlockerService::monitorLinuxKernelEvents).start();

        System.out.println("[BlockerService] Engine running cleanly. Awaiting physical inputs...");
        Looper.loop();
    }

    private static void initProvenServices() {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method getService = sm.getMethod("getService", String.class);

            // 1. Bind WindowManager (Proven)
            IBinder wmBinder = (IBinder) getService.invoke(null, "window");
            if (wmBinder != null) {
                Class<?> wmStub = Class.forName("android.view.IWindowManager$Stub");
                windowManagerService = wmStub.getMethod("asInterface", IBinder.class).invoke(null, wmBinder);
                isKeyguardLockedMethod = windowManagerService.getClass().getMethod("isKeyguardLocked");
            }

            // 2. Bind InputManager (Proven)
            IBinder inputBinder = (IBinder) getService.invoke(null, "input");
            if (inputBinder != null) {
                Class<?> inputStub = Class.forName("android.hardware.input.IInputManager$Stub");
                inputManagerService = inputStub.getMethod("asInterface", IBinder.class).invoke(null, inputBinder);
                injectInputEventMethod = inputManagerService.getClass().getMethod(
                    "injectInputEvent",
                    Class.forName("android.view.InputEvent"),
                    int.class
                );
            }
            System.out.println("[BlockerService] Core framework binders acquired successfully.");
        } catch (Exception e) {
            System.err.println("[BlockerService] Framework fallback engaged: " + e.getMessage());
        }
    }

    private static void monitorLinuxKernelEvents() {
        System.out.println("[BlockerService] Tapping directly into /dev/input streams...");
        try {
            // getevent -q outputs raw linux keycodes directly from the motherboard
            Process process = Runtime.getRuntime().exec("getevent -q");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            // Output format looks like: /dev/input/eventX: 0001 0074 00000001
            // 0001 = EV_KEY
            // 0074 = KEY_POWER (116 in hex)
            // 00000001 = ACTION_DOWN, 00000000 = ACTION_UP

            while ((line = reader.readLine()) != null) {
                if (line.contains("0001 0074 00000001")) {
                    isPowerHeld = true;
                    if (isDeviceLocked()) {
                        // Start the kill-switch timer
                        new Thread(BlockerService::executeLockscreenCountermeasure).start();
                    }
                } else if (line.contains("0001 0074 00000000")) {
                    isPowerHeld = false;
                }
            }
        } catch (Exception e) {
            System.err.println("[BlockerService] Kernel stream severed: " + e.getMessage());
        }
    }

    private static boolean isDeviceLocked() {
        try {
            if (windowManagerService != null && isKeyguardLockedMethod != null) {
                return (boolean) isKeyguardLockedMethod.invoke(windowManagerService);
            }
        } catch (Exception ignored) {}

        // If reflection fails, assume locked to guarantee security
        return true;
    }

    private static void executeLockscreenCountermeasure() {
        try {
            // AOSP Global Actions trigger is 500ms. We wake up just before it.
            Thread.sleep(400);

            if (isPowerHeld) {
                System.out.println("[BlockerService] Malicious lockscreen hold detected! Obliterating menu...");

                // Rapid-fire the BACK key. It intercepts the dialog spawn instantly.
                for (int i = 0; i < 5; i++) {
                    if (!isPowerHeld) break; // Stop firing if they let go early
                    injectBackKey();
                    Thread.sleep(150); // Fire every 150ms to ensure we catch the UI thread
                }
            }
        } catch (InterruptedException ignored) {}
    }

    private static void injectBackKey() {
        try {
            // First choice: High-speed memory injection (Proven to bind)
            if (inputManagerService != null && injectInputEventMethod != null) {
                long now = SystemClock.uptimeMillis();

                KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0);
                KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0);

                // 0 = INJECT_INPUT_EVENT_MODE_ASYNC
                injectInputEventMethod.invoke(inputManagerService, down, 0);
                injectInputEventMethod.invoke(inputManagerService, up, 0);
                return;
            }
        } catch (Exception ignored) {}

        // Indestructible Fallback: If memory injection fails, use raw shell injection
        try {
            Runtime.getRuntime().exec("input keyevent 4");
        } catch (Exception ignored) {}
    }
}
