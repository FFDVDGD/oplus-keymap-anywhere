package io.github.ffdvdgd.opluskeymap;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import android.view.InputDevice;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

public final class KeyboardHook extends XposedModule {
    private static final String TARGET = "com.oplus.games";

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
                    Class<?> manager = pkg.getClassLoader().loadClass(
                            "com.oplus.games.feature.keymousemapping.c");
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
                MouseIdleHook.install(this, pkg.getClassLoader(), context);
                return result;
            });
        } catch (Throwable error) {
            log(Log.ERROR, "GameHubKeyboard", "cannot hook Application.attach", error);
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
