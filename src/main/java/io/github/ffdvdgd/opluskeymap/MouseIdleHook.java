package io.github.ffdvdgd.opluskeymap;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

import io.github.libxposed.api.XposedInterface;

final class MouseIdleHook {
    private static final String TAG = "GameHubMouse";
    private static final String BASE = "com.oplus.games.feature.keymousemapping.";

    private final XposedInterface xposed;
    private final SharedPreferences preferences;
    private final Object lock = new Object();
    private final WeakHashMap<Object, TimerState> timers = new WeakHashMap<>();
    private final ShortcutKeys shortcuts = new ShortcutKeys();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences.OnSharedPreferenceChangeListener listener;
    private final Field touchInstance;
    private final Field contacts;
    private final Field configInstance;
    private final Field banInstance;
    private final Method contactCode;
    private final Method contactTool;
    private final Method mapping;
    private final Method mappingPackage;
    private final Method editing;
    private final Method mouseAllowed;
    private final Method endMouse;
    private Set<String> games = Collections.emptySet();
    private int releaseKey = KeyEvent.KEYCODE_UNKNOWN;
    private int resetKey = KeyEvent.KEYCODE_UNKNOWN;
    private TimerState held;
    private boolean installed;
    private boolean filterActive;
    private boolean mouseEnabled;
    private Runnable forceReset;

    static void install(XposedInterface xposed, ClassLoader loader, Context context) {
        try {
            if ((xposed.getFrameworkProperties() & XposedInterface.PROP_CAP_REMOTE) == 0) {
                xposed.log(Log.WARN, TAG, "remote preferences unavailable; mouse behavior unchanged");
                return;
            }
            new MouseIdleHook(xposed, loader, context);
        } catch (Throwable error) {
            xposed.log(Log.ERROR, TAG, "500 ms feature unavailable; keyboard hooks unchanged", error);
        }
    }

    private MouseIdleHook(XposedInterface xposed, ClassLoader loader, Context context) throws Throwable {
        this.xposed = xposed;
        preferences = xposed.getRemotePreferences(SettingsApp.GROUP);
        Class<?> touch = loader.loadClass(BASE + "TouchControlManagerNew");
        Class<?> contact = loader.loadClass(BASE + "TouchControlManagerNew$c");
        Class<?> config = loader.loadClass(BASE + "edit.manager.KeyMouseConfigManager");
        Class<?> ban = loader.loadClass(BASE + "b");
        Class<?> feature = loader.loadClass(BASE + "KeyMouseMappingFeature");
        Class<?> timer = loader.loadClass(BASE + "TouchControlManagerNew$mouseMove$1");
        touchInstance = singleton(touch);
        contacts = touch.getDeclaredField("c");
        if (!List.class.isAssignableFrom(contacts.getType()) || !Modifier.isStatic(contacts.getModifiers())) {
            throw new NoSuchFieldException("touch contacts type changed");
        }
        contacts.setAccessible(true);
        contactCode = method(contact, "c", int.class);
        contactTool = method(contact, "d", int.class);
        configInstance = singleton(config);
        banInstance = singleton(ban);
        mapping = config.getDeclaredMethod("u", int.class);
        mappingPackage = method(mapping.getReturnType(), "getPkg", String.class);
        editing = method(config, "K", boolean.class);
        mouseAllowed = method(ban, "c", boolean.class);
        endMouse = method(touch, "L", void.class, int.class);
        Method resume = method(timer, "invokeSuspend", Object.class, Object.class);
        Field label = timer.getDeclaredField("label");
        if (label.getType() != int.class) throw new NoSuchFieldException("timer label is not int");
        label.setAccessible(true);
        Method setFilter = method(feature, "setInputFilter", void.class);
        Method unsetFilter = method(feature, "unSetInputFilter", void.class);
        Method disposition = method(feature, "setMouseDisposition", void.class, boolean.class);
        Method stop = method(feature, "gameStop", void.class, String.class, boolean.class);
        Method switchMapping = method(feature, "keyMouseMappingSwitchOpen", void.class,
                boolean.class, boolean.class, boolean.class);
        Method edit = method(config, "O", void.class, boolean.class);
        Method enabled = method(feature, "getKeyMouseMappingSwitch", boolean.class);
        Method inGame = method(feature.getSuperclass(), "getMInGameMode", boolean.class);
        Class<?> devices = loader.loadClass(BASE + "c");
        Field deviceInstance = singleton(devices);
        Method attached = method(devices, "g", boolean.class);

        // Capture the target-loader Unit and exact gesture when the timer starts.
        // Skip only its successful delayed completion; cancellation still runs normally.
        intercept(resume, chain -> {
            synchronized (lock) {
                Object task = chain.getThisObject();
                int phase = label.getInt(task);
                String current = currentGame();
                if (phase == 0) timers.put(task, new TimerState(current, currentContact(), chain.getArg(0)));
                TimerState state = timers.get(task);
                try {
                    if (installed && phase == 1 && state != null && state.unit == chain.getArg(0)
                            && state.contact != null && state.contact == currentContact()
                            && state.pkg != null && state.pkg.equals(current) && games.contains(state.pkg)
                            && filterActive && mouseEnabled
                            && !(Boolean) editing.invoke(configInstance.get(null))
                            && (Boolean) mouseAllowed.invoke(banInstance.get(null))) {
                        // Skip both UP and the flag that would force a new DOWN next time.
                        held = state;
                        return state.unit;
                    }
                    return chain.proceed();
                } finally {
                    if (phase != 0) timers.remove(task);
                }
            }
        });
        intercept(setFilter, chain -> {
            synchronized (lock) {
                Object result = chain.proceed();
                filterActive = (Boolean) enabled.invoke(chain.getThisObject())
                        && (Boolean) inGame.invoke(chain.getThisObject())
                        && (Boolean) attached.invoke(deviceInstance.get(null));
                return result;
            }
        });
        intercept(unsetFilter, chain -> {
            synchronized (lock) {
                filterActive = false;
                shortcuts.clear();
                releaseHeldMouse();
                return chain.proceed();
            }
        });
        intercept(disposition, chain -> {
            synchronized (lock) {
                mouseEnabled = (Boolean) chain.getArg(0);
                if (!mouseEnabled) releaseHeldMouse();
                return chain.proceed();
            }
        });
        intercept(stop, chain -> {
            synchronized (lock) {
                filterActive = false;
                shortcuts.clear();
                releaseHeldMouse();
                return chain.proceed();
            }
        });
        intercept(switchMapping, chain -> {
            synchronized (lock) {
                if (!(Boolean) chain.getArg(0)) {
                    filterActive = false;
                    shortcuts.clear();
                    releaseHeldMouse();
                }
                return chain.proceed();
            }
        });
        intercept(edit, chain -> {
            synchronized (lock) {
                if ((Boolean) chain.getArg(0)) releaseHeldMouse();
                return chain.proceed();
            }
        });
        listener = (prefs, key) -> {
            if (SettingsApp.GAMES.equals(key) || SettingsApp.RELEASE_KEY.equals(key)
                    || SettingsApp.RESET_KEY.equals(key) || key == null) {
                main.post(() -> {
                    synchronized (lock) {
                        readSettings();
                        if (held != null && !games.contains(held.pkg)) {
                            try {
                                releaseHeldMouse();
                            } catch (Throwable error) {
                                xposed.log(Log.ERROR, TAG, "cannot release mouse after list change", error);
                            }
                        }
                    }
                });
            }
        };
        preferences.registerOnSharedPreferenceChangeListener(listener);
        synchronized (lock) {
            readSettings();
            installed = true;
        }
        xposed.log(Log.INFO, TAG, "500 ms hooks installed; initial 20+80 ms task unchanged");
        try {
            installTouchReset(context, loader, touch, contact);
        } catch (Throwable error) {
            xposed.log(Log.ERROR, TAG, "touch reset unavailable; existing hooks unchanged", error);
        }
        // Intercept before the native handler can forward BACK/FORWARD to the game.
        // Match its game/screen guards; never reserve keys in unrelated applications.
        try {
            Class<?> gameState = loader.loadClass("wg0.a");
            Method getGameState = method(gameState, "j", gameState);
            Method playing = method(gameState, "n", boolean.class);
            PowerManager power = context.getSystemService(PowerManager.class);
            KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
            if (power == null || keyguard == null) throw new IllegalStateException("screen services unavailable");
            BooleanSupplier active = () -> {
                try {
                    return filterActive && (Boolean) playing.invoke(getGameState.invoke(null))
                            && power.isInteractive() && !keyguard.isKeyguardLocked();
                } catch (ReflectiveOperationException error) {
                    xposed.log(Log.ERROR, TAG, "cannot determine shortcut game scope", error);
                    return false;
                }
            };
            Class<?> inputListener = loader.loadClass(BASE + "TouchControlManagerNew$d");
            Method input = method(inputListener, "onInputEvent", void.class, InputEvent.class, int.class);
            intercept(input, chain -> {
                synchronized (lock) {
                    ShortcutKeys.Action action = shortcuts.handle((InputEvent) chain.getArg(0),
                            releaseKey, resetKey, active);
                    if (action == ShortcutKeys.Action.PASS) return chain.proceed();
                    try {
                        if (action == ShortcutKeys.Action.RESET) {
                            if (forceReset == null) throw new IllegalStateException("touch reset unavailable");
                            forceReset.run();
                        } else if (action == ShortcutKeys.Action.RELEASE && mouseEnabled
                                && games.contains(currentGame())
                                && !(Boolean) editing.invoke(configInstance.get(null))
                                && (Boolean) mouseAllowed.invoke(banInstance.get(null))
                                && currentContact() != null) {
                            endMouse.invoke(touchInstance.get(null), 2);
                            held = null;
                            xposed.log(Log.INFO, TAG, "manual mouse release");
                        }
                    } catch (Throwable error) {
                        // A failed operation must not leak its bound navigation key.
                        xposed.log(Log.ERROR, TAG, "shortcut operation failed", error);
                    }
                    return null;
                }
            });
            xposed.log(Log.INFO, TAG, "exclusive release/reset shortcuts installed; active games only");
        } catch (Throwable error) {
            xposed.log(Log.ERROR, TAG, "shortcuts unavailable; 500 ms hooks unchanged", error);
        }
    }

    private void installTouchReset(Context context, ClassLoader loader, Class<?> touch, Class<?> contact)
            throws ReflectiveOperationException {
        Class<?> point = loader.loadClass(BASE + "PointF");
        Class<?> jobType = loader.loadClass("kotlinx.coroutines.Job");
        Method cancel = method(jobType, "cancel", void.class, CancellationException.class);
        Method completed = method(jobType, "isCompleted", boolean.class);
        Method stopTasks = method(touch, "s", void.class);
        Method resetMouse = method(touch, "r", void.class);
        Method contactId = method(contact, "a", int.class);
        Method position = method(contact, "b", point);
        Class<?> shoulder = loader.loadClass("com.oplus.games.shoulderkey.ShoulderKeyLibUtil");
        Object inputManager = method(shoulder, "c", Object.class).invoke(singleton(shoulder).get(null));
        Class<?> inputServiceType = loader.loadClass("android.hardware.input.IOplusInputManager");
        Field inputServiceField = inputManager.getClass().getDeclaredField("mIOplusInputManager");
        inputServiceField.setAccessible(true);
        Object inputService = inputServiceField.get(inputManager);
        if (inputService == null) throw new IllegalStateException("OPlus input service unavailable");
        Method inject = method(inputServiceType, "injectShoulderTouchEvent", void.class,
                MotionEvent.class, int.class);
        String[] names = {"n", "o", "p", "q", "e", "f"};
        Field[] state = new Field[names.length];
        for (int i = 0; i < names.length; i++) {
            Field field = touch.getDeclaredField(names[i]);
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != (i < 4 ? jobType : point)) {
                throw new NoSuchFieldException("touch reset state changed: " + names[i]);
            }
            field.setAccessible(true);
            state[i] = field;
        }
        class ResetReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!isOrderedBroadcast()) return;
                if (SettingsApp.RELEASE_MOUSE.equals(intent.getAction())) {
                    int result = Activity.RESULT_OK;
                    try {
                        synchronized (lock) {
                            Object mouse = currentContact();
                            // The module UI may be outside game mode, where the OEM
                            // wrapper drops UP. Release this known mouse contact directly.
                            if (mouse != null) {
                                PointF location = (PointF) position.invoke(mouse);
                                long now = SystemClock.uptimeMillis();
                                MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP,
                                        location.x, location.y, 0);
                                up.setSource(0x1002);
                                try {
                                    inject.invoke(inputService, up, (Integer) contactId.invoke(mouse) + 11);
                                } finally {
                                    up.recycle();
                                }
                            }
                            endMouse.invoke(touchInstance.get(null), 2);
                            held = null;
                            xposed.log(Log.INFO, TAG, "manual mouse release from module UI");
                        }
                    } catch (Throwable error) {
                        xposed.log(Log.ERROR, TAG, "manual mouse release from module UI failed", error);
                        result = Activity.RESULT_FIRST_USER;
                    }
                    setResultCode(result);
                    return;
                }
                if (!SettingsApp.RESET_TOUCHES.equals(intent.getAction())) return;
                PendingResult pending = goAsync();
                reset(code -> {
                    pending.setResultCode(code);
                    pending.finish();
                });
            }

            void reset(IntConsumer completion) {
                reset(completion, new ArrayList<>(), SystemClock.uptimeMillis() + 2000);
            }

            private void reset(IntConsumer completion, List<Object> jobs, long deadline) {
                int result = Activity.RESULT_OK;
                try {
                    synchronized (lock) {
                        for (int i = 0; i < 4; i++) {
                            Object job = state[i].get(null);
                            if (job != null && !jobs.contains(job)) {
                                jobs.add(job);
                                cancel.invoke(job, (Object) null);
                            }
                        }
                        for (Object job : jobs) {
                            if (!(Boolean) completed.invoke(job)) {
                                if (SystemClock.uptimeMillis() >= deadline) {
                                    throw new IllegalStateException("touch tasks did not finish cancellation");
                                }
                                // Cancellation is asynchronous on the OEM IO/SINGLE dispatchers.
                                // Wait without blocking the main thread before resetting flags.
                                main.postDelayed(() -> reset(completion, jobs, deadline), 10);
                                return;
                            }
                        }
                        Object manager = touchInstance.get(null);
                        stopTasks.invoke(manager);
                        held = null;
                        timers.clear();
                        List<?> active = (List<?>) contacts.get(null);
                        int released = active.size();
                        // Native merge state can outlive the game's contact list/process.
                        // Use the OEM service directly: the game wrapper drops UP outside
                        // game mode. Only release the mapping namespace (LX keys start at 30).
                        long now = SystemClock.uptimeMillis();
                        MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 0, 0, 0);
                        up.setSource(0x1002);
                        try {
                            for (Object item : active) {
                                PointF location = (PointF) position.invoke(item);
                                up.setLocation(location.x, location.y);
                                inject.invoke(inputService, up, (Integer) contactId.invoke(item) + 11);
                            }
                            up.setLocation(0, 0);
                            for (int id = 11; id < 30; id++) inject.invoke(inputService, up, id);
                        } finally {
                            up.recycle();
                        }
                        active.clear();
                        resetMouse.invoke(manager);
                        for (Field field : state) field.set(null, null);
                        xposed.log(Log.INFO, TAG, "force touch reset completed; released " + released
                                + " mapping contacts; native mapping UP sweep sent");
                    }
                } catch (Throwable error) {
                    xposed.log(Log.ERROR, TAG, "force touch reset failed", error);
                    result = Activity.RESULT_FIRST_USER;
                }
                completion.accept(result);
            }
        }
        ResetReceiver receiver = new ResetReceiver();
        IntentFilter actions = new IntentFilter(SettingsApp.RESET_TOUCHES);
        actions.addAction(SettingsApp.RELEASE_MOUSE);
        context.registerReceiver(receiver, actions,
                SettingsApp.RESET_PERMISSION, main,
                Build.VERSION.SDK_INT >= 33 ? Context.RECEIVER_EXPORTED : 0);
        forceReset = () -> receiver.reset(code -> {});
        xposed.log(Log.INFO, TAG, "signature-protected touch reset receiver installed");
    }

    private void readSettings() {
        games = new HashSet<>(preferences.getStringSet(SettingsApp.GAMES, Collections.emptySet()));
        releaseKey = preferences.getInt(SettingsApp.RELEASE_KEY, KeyEvent.KEYCODE_UNKNOWN);
        resetKey = preferences.getInt(SettingsApp.RESET_KEY, KeyEvent.KEYCODE_UNKNOWN);
    }

    private String currentGame() throws ReflectiveOperationException {
        Object value = mapping.invoke(configInstance.get(null), -1002);
        return value == null ? null : (String) mappingPackage.invoke(value);
    }

    private Object currentContact() throws ReflectiveOperationException {
        for (Object contact : (List<?>) contacts.get(null)) {
            if ((Integer) contactCode.invoke(contact) == 2 && (Integer) contactTool.invoke(contact) == 3) {
                return contact;
            }
        }
        return null;
    }

    private void releaseHeldMouse() throws ReflectiveOperationException {
        if (held == null) return;
        // The OEM mouse reset cancels the pending 500 ms job, releases only pointer 2 /
        // tool type 3 at its last position, and makes the next movement create a DOWN.
        // Do not release a replacement gesture after a normal boundary reset.
        if (held.contact == currentContact()) {
            endMouse.invoke(touchInstance.get(null), 2);
            xposed.log(Log.INFO, TAG, "released held mouse for " + held.pkg);
        }
        held = null;
    }

    private void intercept(Method method, XposedInterface.Hooker hooker) {
        xposed.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(hooker);
    }

    private static Field singleton(Class<?> type) throws ReflectiveOperationException {
        Field field = type.getDeclaredField("a");
        if (field.getType() != type || !Modifier.isStatic(field.getModifiers())) {
            throw new NoSuchFieldException(type.getName() + ".a type changed");
        }
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> type, String name, Class<?> result, Class<?>... args)
            throws ReflectiveOperationException {
        Method method = type.getDeclaredMethod(name, args);
        if (method.getReturnType() != result) throw new NoSuchMethodException(type.getName() + "." + name);
        method.setAccessible(true);
        return method;
    }

    private static final class TimerState {
        final String pkg;
        final Object contact;
        final Object unit;

        TimerState(String pkg, Object contact, Object unit) {
            this.pkg = pkg;
            this.contact = contact;
            this.unit = unit;
        }
    }
}
