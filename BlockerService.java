import android.content.Context;
import android.app.KeyguardManager;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class BlockerService {
    private static final int KEYCODE_POWER = 26;
    private static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;

    private static Object inputManagerService = null;
    private static KeyguardManager keyguardManager = null;
    private static Method injectInputEventMethod = null;

    public static void main(String[] args) {
        System.out.println("[BlockerService] Booting Virtual Key-Release Engine...");

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        if (!initSystemServices()) {
            System.err.println("[BlockerService] CRITICAL: Service initialization failed. Aborting.");
            return;
        }

        registerInputSpy();

        System.out.println("[BlockerService] Engine running. Waiting for physical inputs...");
        Looper.loop();
    }

    private static boolean initSystemServices() {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method getService = sm.getMethod("getService", String.class);

            // Bind IInputManager
            IBinder inputBinder = (IBinder) getService.invoke(null, "input");
            Class<?> inputStub = Class.forName("android.hardware.input.IInputManager$Stub");
            inputManagerService = inputStub.getMethod("asInterface", IBinder.class).invoke(null, inputBinder);

            // Pre-resolve the injection method so we don't waste time during the event
            injectInputEventMethod = inputManagerService.getClass().getMethod(
                "injectInputEvent",
                Class.forName("android.view.InputEvent"),
                int.class
            );

            // Create a System Context to cleanly get the KeyguardManager
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
            if (activityThread == null) {
                activityThread = activityThreadClass.getMethod("systemMain").invoke(null);
            }
            Context systemContext = (Context) activityThreadClass.getMethod("getSystemContext").invoke(activityThread);

            keyguardManager = (KeyguardManager) systemContext.getSystemService(Context.KEYGUARD_SERVICE);

            return inputManagerService != null && keyguardManager != null;
        } catch (Exception e) {
            System.err.println("[BlockerService] Framework binding failed!");
            e.printStackTrace(); // Actually print the error this time
            return false;
        }
    }

    private static void registerInputSpy() {
        try {
            Class<?> inputEventListenerClass = Class.forName("android.hardware.input.IInputEventListener");

            Object inputEventListenerProxy = Proxy.newProxyInstance(
                BlockerService.class.getClassLoader(),
                new Class<?>[]{inputEventListenerClass},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getName().equals("onInputEvent")) {
                            handleRawInput(args[0]);
                        }
                        return null;
                    }
                }
            );

            Method registerMethod = inputManagerService.getClass().getMethod(
                "registerInputEventListener",
                inputEventListenerClass
            );
            registerMethod.invoke(inputManagerService, inputEventListenerProxy);

        } catch (Exception e) {
            System.err.println("[BlockerService] Failed to attach to input stream:");
            e.printStackTrace();
        }
    }

    private static void handleRawInput(Object inputEvent) {
        try {
            if (!(inputEvent instanceof KeyEvent)) return;

            KeyEvent keyEvent = (KeyEvent) inputEvent;

            // We only care about the moment the Power button is PRESSED DOWN
            if (keyEvent.getKeyCode() == KEYCODE_POWER && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {

                // Check if device is locked
                if (keyguardManager != null && keyguardManager.isDeviceLocked()) {
                    System.out.println("[BlockerService] Power DOWN intercepted on lockscreen. Spoofing release...");
                    injectFakePowerRelease();
                }
            }
        } catch (Exception e) {
            System.err.println("[BlockerService] Input parsing error: " + e.getMessage());
        }
    }

    private static void injectFakePowerRelease() {
        try {
            long now = SystemClock.uptimeMillis();

            // Construct a synthetic ACTION_UP event for the Power Key
            KeyEvent fakeUpEvent = new KeyEvent(
                now,
                now,
                KeyEvent.ACTION_UP,
                KEYCODE_POWER,
                0,
                0,
                -1, // VIRTUAL_KEYBOARD
                0,
                KeyEvent.FLAG_FROM_SYSTEM,
                257 // SOURCE_KEYBOARD
            );

            // Fire it directly into the OS event queue
            injectInputEventMethod.invoke(inputManagerService, fakeUpEvent, INJECT_INPUT_EVENT_MODE_ASYNC);
            System.out.println("[BlockerService] Fake Power UP injected successfully. Long-press aborted.");

        } catch (Exception e) {
            System.err.println("[BlockerService] Failed to inject synthetic event:");
            e.printStackTrace();
        }
    }
}
