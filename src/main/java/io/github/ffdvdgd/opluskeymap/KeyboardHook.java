package io.github.ffdvdgd.opluskeymap;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

public final class KeyboardHook extends XposedModule {
    private static final String TARGET = "com.oplus.games";
    static final Object INPUT_LOCK = new Object();

    @Override
    public void onPackageReady(PackageReadyParam pkg) {
        if (!TARGET.equals(pkg.getPackageName()) || !TARGET.equals(Application.getProcessName())) {
            return;
        }

        try {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            hook(attach).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result = chain.proceed();
                Context context = (Context) chain.getArg(0);
                try {
                    long version = context.getPackageManager()
                            .getPackageInfo(TARGET, 0).getLongVersionCode();
                    Class<?> manager = keyboardManager(pkg.getClassLoader());
                    Method official = manager.getDeclaredMethod("i", InputDevice.class);
                    Method external = InputDevice.class.getDeclaredMethod("isExternal");
                    if (official.getReturnType() != boolean.class) {
                        log(Log.WARN, "GameHubKeyboard", "keyboard check is not boolean in version "
                                + version + "; no changes applied");
                        return result;
                    }
                    hook(official).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(check -> {
                        Object original = check.proceed();
                        InputDevice device = (InputDevice) check.getArg(0);
                        return isAttachedKeyboard(device) ? Boolean.TRUE : original;
                    });
                    // OPlus classifies scrcpy's BUS_VIRTUAL UHID device as
                    // internal. Normalize attached full keyboards only in
                    // the game assistant, never in system_server or the game.
                    hook(external).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(check -> {
                        Object original = check.proceed();
                        InputDevice device = (InputDevice) check.getThisObject();
                        return isAttachedKeyboard(device) ? Boolean.TRUE : original;
                    });
                    log(Log.INFO, "GameHubKeyboard", "vendor gate removed for "
                            + TARGET + " " + version);
                } catch (Throwable error) {
                    log(Log.ERROR, "GameHubKeyboard", "hook not installed", error);
                }
                installFunctionKeys(pkg.getClassLoader());
                MouseIdleHook.install(this, pkg.getClassLoader(), context);
                WheelHook.install(this, pkg.getClassLoader());
                return result;
            });
        } catch (Throwable error) {
            log(Log.ERROR, "GameHubKeyboard", "cannot hook Application.attach", error);
        }
    }

    static Class<?> keyboardManager(ClassLoader loader) throws ClassNotFoundException {
        try {
            return loader.loadClass("com.oplus.games.feature.keymousemapping.KeyboardManager");
        } catch (ClassNotFoundException ignored) {
            return loader.loadClass("com.oplus.games.feature.keymousemapping.c");
        }
    }

    private void installFunctionKeys(ClassLoader loader) {
        try {
            String base = "com.oplus.games.feature.keymousemapping.";
            boolean named = keyboardManager(loader).getName().equals(base + "KeyboardManager");
            Class<?> conversion = loader.loadClass(named ? base + "util.KeyCodeConversion" : "wf0.a");
            Class<?> touch = loader.loadClass(base + "TouchControlManagerNew");
            Class<?> listener = loader.loadClass("android.hardware.input.OplusInputManager$InputFilterListener");
            Method label = conversion.getDeclaredMethod("f", int.class);
            Method code = conversion.getDeclaredMethod("b", String.class);
            Method input = touch.getDeclaredMethod(named ? "E" : "G", listener, KeyEvent.class, int.class);
            Method forward = touch.getDeclaredMethod(named ? "n" : "p", listener, InputEvent.class, int.class);
            if (label.getReturnType() != String.class || code.getReturnType() != Integer.class
                    || input.getReturnType() != void.class || forward.getReturnType() != void.class) {
                throw new NoSuchMethodException("function key signatures changed");
            }
            for (Method method : new Method[] {label, code, input, forward}) method.setAccessible(true);

            ThreadLocal<KeyEvent> reserved = new ThreadLocal<>();
            hook(forward).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
                KeyEvent event = reserved.get();
                return event != null && event == chain.getArg(1) ? null : chain.proceed();
            });
            // The native listener has already checked game/screen scope here.
            // Run mapping normally, suppressing only its fallback forwarding.
            hook(input).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
                KeyEvent event = (KeyEvent) chain.getArg(1);
                if (!FunctionKeys.contains(event.getKeyCode())) return chain.proceed();
                KeyEvent previous = reserved.get();
                reserved.set(event);
                try {
                    return chain.proceed();
                } finally {
                    reserved.set(previous);
                }
            });
            hook(label).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
                String value = FunctionKeys.label((Integer) chain.getArg(0));
                return value != null ? value : chain.proceed();
            });
            hook(code).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
                Integer value = FunctionKeys.code((String) chain.getArg(0));
                return value != null ? value : chain.proceed();
            });
            log(Log.INFO, "GameHubKeyboard", "F1-F12 mapping and game-only interception installed");
        } catch (Throwable error) {
            log(Log.ERROR, "GameHubKeyboard", "function key hooks not installed", error);
        }
    }

    private static boolean isAttachedKeyboard(InputDevice device) {
        // Public equivalent of isFullKeyboard(), excluding Android's SDK
        // injection keyboard (device id -1), but allowing real UHID devices.
        return !device.isVirtual()
                && device.supportsSource(InputDevice.SOURCE_KEYBOARD)
                && device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC;
    }
}
