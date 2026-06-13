import android.os.IBinder;
import android.os.Looper;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class BlockerService {
    private static final int KEYCODE_POWER = 26;
    private static final long HOLD_THRESHOLD_MS = 210;

    private static Object windowManagerService = null;
    private static Object inputManagerService = null;

    // Marked volatile to ensure cross-thread memory synchronization
    private static volatile boolean isHoldingPower = false;
    private static volatile long powerKeyDownTime = 0;

    public static void main(String[] args) {
        System.out.println("[BlockerService] Initializing pure Java API daemon...");

        // 1. Setup the binder connections to the internal OS managers
        initSystemServices();

        // 2. Register our native Java input monitor hook directly into the OS framework
        registerInputMonitor();

        // 3. Keep the process alive and listening using the thread's event loop
        Looper.prepare();
        Looper.loop();
    }

    private static void initSystemServices() {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method getService = sm.getMethod("getService", String.class);

            // Bind WindowManager
            IBinder wmBinder = (IBinder) getService.invoke(null, "window");
            Class<?> wmStub = Class.forName("android.view.IWindowManager$Stub");
            windowManagerService = wmStub.getMethod("asInterface", IBinder.class).invoke(null, wmBinder);

            // Bind InputManager
            IBinder inputBinder = (IBinder) getService.invoke(null, "input");
            Class<?> inputStub = Class.forName("android.hardware.input.IInputManager$Stub");
            inputManagerService = inputStub.getMethod("asInterface", IBinder.class).invoke(null, inputBinder);
        } catch (Exception e) {
            System.err.println("[BlockerService] Failed to initialize system interfaces.");
            e.printStackTrace();
        }
    }

    private static void registerInputMonitor() {
        if (inputManagerService == null) return;
        try {
            Class<?> inputEventListenerClass = Class.forName("android.hardware.input.IInputEventListener");

            Object inputEventListenerProxy = Proxy.newProxyInstance(
                BlockerService.class.getClassLoader(),
                new Class<?>[]{inputEventListenerClass},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getName().equals("onInputEvent")) {
                            Object inputEvent = args[0];
                            handleInputEvent(inputEvent);
                        }
                        return null;
                    }
                }
            );

            // Register the proxy into the OS pipeline
            Method registerMethod = inputManagerService.getClass().getMethod("registerInputEventListener", inputEventListenerClass);
            registerMethod.invoke(inputManagerService, inputEventListenerProxy);
            System.out.println("[BlockerService] Native Java Input Event Listener registered successfully.");

        } catch (Exception e) {
            System.err.println("[BlockerService] Critical error bypassing standard input pipeline:");
            e.printStackTrace();
        }
    }

    private static void handleInputEvent(Object inputEvent) {
        try {
            Class<?> keyEventClass = Class.forName("android.view.KeyEvent");

            if (keyEventClass.isInstance(inputEvent)) {
                Method getKeyCode = keyEventClass.getMethod("getKeyCode");
                Method getAction = keyEventClass.getMethod("getAction");

                int keyCode = (int) getKeyCode.invoke(inputEvent);
                int action = (int) getAction.invoke(inputEvent); // 0 = DOWN, 1 = UP

                if (keyCode == KEYCODE_POWER) {
                    if (action == 0) { // Key Down
                        if (!isHoldingPower) {
                            isHoldingPower = true;
                            powerKeyDownTime = System.currentTimeMillis();

                            // Spin up an isolated supervisor timing thread for this specific sequence
                            new Thread(BlockerService::evaluateHoldSequence).start();
                        }
                    } else if (action == 1) { // Key Up
                        isHoldingPower = false;
                        powerKeyDownTime = 0;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void evaluateHoldSequence() {
        try {
            long currentTriggerTime = powerKeyDownTime;
            Thread.sleep(HOLD_THRESHOLD_MS);

            // Verify if the button is still physically compressed
            if (isHoldingPower && powerKeyDownTime == currentTriggerTime) {
                if (shouldBlockPowerMenu()) {
                    injectPowerKeyToggle();
                }
                powerKeyDownTime = 0; // Clear trigger sequence
            }
        } catch (InterruptedException ignored) {}
    }

    private static boolean shouldBlockPowerMenu() {
        if (windowManagerService == null) return false;
        try {
            // Is the device currently at the lock screen interface? (Handles camera overlay modes)
            Method isLocked = windowManagerService.getClass().getMethod("isKeyguardLocked");
            boolean keyguardActive = (boolean) isLocked.invoke(windowManagerService);

            // Is the OS inflating the Power Menu (Global Actions Dialog)?
            Method isGlobalActionsShowing = windowManagerService.getClass().getMethod("isGlobalActionsShowing");
            boolean powerMenuVisible = (boolean) isGlobalActionsShowing.invoke(windowManagerService);

            return (powerMenuVisible || keyguardActive);
        } catch (Exception e) {
            // Fallback safety bounds
            return true;
        }
    }

    private static void injectPowerKeyToggle() {
        if (inputManagerService == null) return;
        try {
            Class<?> keyEventClass = Class.forName("android.view.KeyEvent");
            Method injectMethod = inputManagerService.getClass().getMethod("injectInputEvent",
                    Class.forName("android.view.InputEvent"), Integer.TYPE);

            // Inject Key Down (Action 0)
            Object keyDown = keyEventClass.getConstructor(Integer.TYPE, Integer.TYPE).newInstance(0, KEYCODE_POWER);
            injectMethod.invoke(inputManagerService, keyDown, 0); // 0 = ASYNC MODE

            // Inject Key Up (Action 1)
            Object keyUp = keyEventClass.getConstructor(Integer.TYPE, Integer.TYPE).newInstance(1, KEYCODE_POWER);
            injectMethod.invoke(inputManagerService, keyUp, 0);

            System.out.println("[BlockerService] Collapsed Power Menu interface safely.");
        } catch (Exception e) {
            System.err.println("[BlockerService] Injection failed.");
            e.printStackTrace();
        }
    }
}
