package io.github.ffdvdgd.opluskeymap;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;

final class MouseIdleHook {
    private static final String TAG = "GameHubMouse";
    private static final String BASE = "com.oplus.games.feature.keymousemapping.";

    private final XposedInterface xposed;
    private final SharedPreferences preferences;
    private final Object lock = new Object();
    private final WeakHashMap<Object, TimerState> timers = new WeakHashMap<>();
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
    private TimerState held;
    private boolean installed;
    private boolean filterActive;
    private boolean mouseEnabled;

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
                releaseHeldMouse();
                return chain.proceed();
            }
        });
        intercept(switchMapping, chain -> {
            synchronized (lock) {
                if (!(Boolean) chain.getArg(0)) {
                    filterActive = false;
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
            if (SettingsApp.GAMES.equals(key) || key == null) {
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
    }

    private void readSettings() {
        games = new HashSet<>(preferences.getStringSet(SettingsApp.GAMES, Collections.emptySet()));
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
