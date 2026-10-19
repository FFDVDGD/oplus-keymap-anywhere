package io.github.ffdvdgd.opluskeymap;

import android.graphics.PointF;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;

import static io.github.ffdvdgd.opluskeymap.DirectionalWheel.Direction;
import static io.github.ffdvdgd.opluskeymap.DirectionalWheel.Key;
import static io.github.ffdvdgd.opluskeymap.DirectionalWheel.Point;

final class WheelHook implements DirectionalWheel.Backend {
    private static final String BASE = "com.oplus.games.feature.keymousemapping.";
    private static final String TAG = "GameHubWheels";
    private static volatile WheelHook instance;
    private final XposedInterface xposed;
    private final Map<Object, DirectionalWheel> wheels = new IdentityHashMap<>();
    private final ThreadLocal<Boolean> adding = new ThreadLocal<>();
    private final ThreadLocal<Dispatch> dispatch = new ThreadLocal<>();
    private final Object touch;
    private final Object config;
    private final Object ban;
    private final Object conversion;
    private final Field contacts;
    private final Method configurations;
    private final Method type;
    private final Method combine;
    private final Method[] directions = new Method[5];
    private final Method keyCode;
    private final Method keyX;
    private final Method keyY;
    private final Method content;
    private final Method lookup;
    private final Method alternative;
    private final Method convertible;
    private final Method keyboardAllowed;
    private final Method contactId;
    private final Method contactCode;
    private final Method contactTool;
    private final Method contactPoint;
    private final Constructor<?> point;
    private final Method convert;
    private final Method jitter;
    private final Method jitterOffset;
    private final Method down;
    private final Method move;
    private final Method up;
    private final Object inputService;
    private final Method inject;
    private final float fastOffset;
    private final DirectionalWheel.Scheduler scheduler;
    private final HandlerThread worker;
    private final Handler handler;
    private volatile boolean ready;
    private boolean resetting;

    static void install(XposedInterface xposed, ClassLoader loader) {
        try {
            WheelHook hook = new WheelHook(xposed, loader);
            hook.ready = true;
            xposed.log(Log.INFO, TAG, "multiple directional wheels installed");
        } catch (Throwable error) {
            try {
                Thread worker = uninstall();
                if (worker != null) worker.join();
            } catch (Throwable cleanup) {
                error.addSuppressed(cleanup);
            }
            xposed.log(Log.ERROR, TAG, "multiple wheels unavailable; editor limit unchanged", error);
        }
    }

    private WheelHook(XposedInterface xposed, ClassLoader loader) throws ReflectiveOperationException {
        this.xposed = xposed;
        boolean named = KeyboardHook.keyboardManager(loader).getName().equals(BASE + "KeyboardManager");
        Class<?> touchType = loader.loadClass(BASE + "TouchControlManagerNew");
        Class<?> configType = loader.loadClass(BASE + "edit.manager.KeyMouseConfigManager");
        Class<?> banType = loader.loadClass(BASE + (named ? "a" : "b"));
        Class<?> conversionType = loader.loadClass(named ? BASE + "util.KeyCodeConversion" : "wf0.a");
        Class<?> contactType = loader.loadClass(BASE + "TouchControlManagerNew$" + (named ? "b" : "c"));
        Class<?> pointType = loader.loadClass(BASE + "PointF");
        Class<?> listener = loader.loadClass("android.hardware.input.OplusInputManager$InputFilterListener");
        touch = singleton(touchType);
        config = singleton(configType);
        ban = singleton(banType);
        conversion = singleton(conversionType);
        contacts = touchType.getDeclaredField("c");
        if (!List.class.isAssignableFrom(contacts.getType()) || !Modifier.isStatic(contacts.getModifiers())) {
            throw new NoSuchFieldException("touch contacts type changed");
        }
        contacts.setAccessible(true);
        configurations = method(configType, "z");
        lookup = method(configType, "u", int.class);
        Class<?> mappingType = lookup.getReturnType();
        type = method(mappingType, "getType");
        combine = method(mappingType, "getCombineContent");
        Class<?> combineType = combine.getReturnType();
        String[] names = {"getTop", "getBottom", "getLeft", "getRight", "getCenter"};
        for (int i = 0; i < names.length; i++) directions[i] = method(combineType, names[i]);
        Class<?> keyType = directions[0].getReturnType();
        keyCode = method(keyType, "getKeyCode");
        keyX = method(keyType, "getKeyX");
        keyY = method(keyType, "getKeyY");
        content = method(keyType, "getContent");
        alternative = method(conversionType, "a", int.class);
        convertible = method(conversionType, "e", int.class);
        keyboardAllowed = method(banType, named ? "a" : "b");
        contactId = method(contactType, "a");
        contactPoint = method(contactType, "b");
        contactCode = method(contactType, "c");
        contactTool = method(contactType, "d");
        point = pointType.getDeclaredConstructor(float.class, float.class);
        convert = method(touchType, named ? "F" : "H", pointType);
        jitter = method(touchType, named ? "y" : "A", pointType);
        jitterOffset = method(touchType, named ? "x" : "z", float.class);
        down = method(touchType, named ? "N" : "P", int.class, pointType, int.class,
                boolean.class, Integer.class);
        move = method(touchType, named ? "P" : "R", pointType, int.class, boolean.class);
        up = method(touchType, named ? "Q" : "S", int.class, pointType, int.class,
                boolean.class, InputEvent.class, Integer.class);
        Class<?> device = loader.loadClass("com.coloros.gamespaceui.utils." + (named ? "u" : "y"));
        Class<?> geometry = loader.loadClass(named ? "cc0.a" : "wf0.b");
        fastOffset = (Float) method(geometry,
                (Boolean) method(device, "b").invoke(singleton(device)) ? "e" : "f")
                .invoke(singleton(geometry));
        Class<?> shoulder = loader.loadClass("com.oplus.games.shoulderkey.ShoulderKeyLibUtil");
        Object inputManager = method(shoulder, "c").invoke(singleton(shoulder));
        Field service = inputManager.getClass().getDeclaredField("mIOplusInputManager");
        service.setAccessible(true);
        inputService = service.get(inputManager);
        if (inputService == null) throw new IllegalStateException("OPlus input service unavailable");
        inject = method(loader.loadClass("android.hardware.input.IOplusInputManager"),
                "injectShoulderTouchEvent", MotionEvent.class, int.class);

        worker = new HandlerThread("KeymapWheels");
        instance = this;
        worker.start();
        handler = new Handler(worker.getLooper());
        scheduler = new DirectionalWheel.Scheduler() {
            public void post(Runnable task) { handler.postDelayed(task, 1); }
            public void remove(Runnable task) { handler.removeCallbacks(task); }
        };

        // Extend native lookup/validation, but keep its direct-key and first-wheel priority.
        intercept(lookup, chain -> {
            Object result = chain.proceed();
            if (!ready || result != null) return result;
            return findWheel((Integer) chain.getArg(0));
        });
        intercept(method(configType, "i", String.class), chain -> {
            Object result = chain.proceed();
            if (!ready || Boolean.TRUE.equals(result)) return result;
            for (Object mapping : mappings()) {
                if ((Integer) type.invoke(mapping) != 2) continue;
                Object keys = combine.invoke(mapping);
                if (keys == null) continue;
                for (int i = 0; i < 4; i++) {
                    Object key = directions[i].invoke(keys);
                    if (key != null && chain.getArg(0).equals(content.invoke(key))) return true;
                }
            }
            return false;
        });
        // Leave screen/game/edit/ban guards in the original key handler.
        intercept(method(touchType, named ? "E" : "G", listener, KeyEvent.class, int.class), chain -> {
            if (!ready) return chain.proceed();
            synchronized (KeyboardHook.INPUT_LOCK) {
                if (!ready) return chain.proceed();
                Dispatch previous = dispatch.get();
                dispatch.set(new Dispatch());
                try {
                    return chain.proceed();
                } finally {
                    dispatch.set(previous);
                    if (!(Boolean) keyboardAllowed.invoke(ban)) stopAll();
                }
            }
        });
        intercept(method(touchType, named ? "w" : "y", KeyEvent.class, Boolean.class), chain -> {
            Dispatch current = dispatch.get();
            if (!ready || current == null) return chain.proceed();
            KeyEvent event = (KeyEvent) chain.getArg(0);
            int code = event.getKeyCode();
            Object mapping = lookup.invoke(config, code);
            if (mapping == null && (Boolean) convertible.invoke(conversion, code)) {
                Integer other = (Integer) alternative.invoke(conversion, code);
                if (other != null) {
                    code = other;
                    mapping = lookup.invoke(config, code);
                }
            }
            if (mapping == null || (Integer) type.invoke(mapping) != 2 || mapping == firstWheel()) {
                return chain.proceed();
            }
            // Native repeats are forwarded, without modifying the wheel's pressed-key list.
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() != 0) return null;
            current.handled = true;
            if (resetting) return null;
            try {
                List<Key> keys = keys(mapping);
                DirectionalWheel wheel = wheels.get(mapping);
                if (wheel == null) {
                    Point left = keys.stream().filter(k -> k.direction() == Direction.LEFT)
                            .findFirst().get().point();
                    Point right = keys.stream().filter(k -> k.direction() == Direction.RIGHT)
                            .findFirst().get().point();
                    wheel = new DirectionalWheel((Integer) keyCode.invoke(mapping), left.midpoint(right),
                            fastOffset, this, scheduler, KeyboardHook.INPUT_LOCK);
                    wheels.put(mapping, wheel);
                }
                for (Key key : keys) {
                    if (key.code() != code) continue;
                    if (event.getAction() == KeyEvent.ACTION_DOWN) wheel.down(key);
                    else if (event.getAction() == KeyEvent.ACTION_UP) wheel.up(key.direction());
                    break;
                }
            } catch (Exception error) {
                failed(error);
            }
            return null;
        });
        intercept(method(touchType, named ? "n" : "p", listener, InputEvent.class, int.class), chain -> {
            Dispatch current = dispatch.get();
            return ready && current != null && current.handled ? null : chain.proceed();
        });
        // A native mouse/keyboard callback can recycle a slot on another dispatcher.
        // Serialize contact replacement with our identity check and MOVE injection.
        for (Method mutation : new Method[] {down, up, method(touchType, named ? "o" : "q", int.class)}) {
            intercept(mutation, chain -> {
                if (!ready) return chain.proceed();
                synchronized (KeyboardHook.INPUT_LOCK) {
                    return chain.proceed();
                }
            });
        }
        intercept(method(configType, "O", boolean.class), chain -> {
            synchronized (KeyboardHook.INPUT_LOCK) {
                if (ready && (Boolean) chain.getArg(0)) stopAll();
                return chain.proceed();
            }
        });
        Class<?> feature = loader.loadClass(BASE + "KeyMouseMappingFeature");
        for (Method stop : new Method[] {method(configType, "f"),
                method(touchType, named ? "q" : "s"), method(feature, "unSetInputFilter")}) {
            intercept(stop, chain -> {
                synchronized (KeyboardHook.INPUT_LOCK) {
                    if (ready) stopAll();
                    return chain.proceed();
                }
            });
        }

        // Remove the editor restriction only after runtime hooks are all in place.
        Class<?> editor = loader.loadClass(BASE + "edit.KeyMouseMappingMainView");
        Class<?> views = loader.loadClass(BASE + "edit.manager.MappingViewManager");
        Method initialized = method(configType, "B");
        intercept(method(views, named ? "f" : "j", int.class), chain ->
                ready && Boolean.TRUE.equals(adding.get()) && (Integer) chain.getArg(0) == 2
                        ? null : chain.proceed());
        intercept(method(editor, named ? "o" : "p", loader.loadClass("kotlin.Pair"), int.class, int.class), chain -> {
            if (!ready || (Integer) chain.getArg(1) != 2) return chain.proceed();
            Object[] args = chain.getArgs().toArray();
            if (firstWheel() != null) {
                int id = -2000;
                List<Integer> used = new ArrayList<>();
                for (Object mapping : mappings()) used.add((Integer) keyCode.invoke(mapping));
                while (used.contains(id)) id--;
                args[2] = id;
            }
            Boolean previous = adding.get();
            adding.set(true);
            try {
                return chain.proceed(args);
            } finally {
                adding.set(previous);
            }
        });
        intercept(method(editor, "setDirectionalWheelEnableState", boolean.class), chain -> {
            if (!ready || !(Boolean) initialized.invoke(config)) return chain.proceed();
            return chain.proceed(new Object[] {true});
        });
    }

    static boolean canReload() {
        if (instance == null) return true;
        if (instance.resetting) return false;
        return instance.wheels.values().stream().allMatch(DirectionalWheel::isIdle);
    }

    // Called with INPUT_LOCK held; join the returned thread only after releasing it.
    static Thread uninstall() throws ReflectiveOperationException {
        if (instance == null) return null;
        WheelHook old = instance;
        old.ready = false;
        old.stopAll();
        if (old.handler != null) old.handler.removeCallbacksAndMessages(null);
        old.worker.quit();
        instance = null;
        return old.worker;
    }

    static void beginReset() throws ReflectiveOperationException {
        if (instance == null || instance.resetting) return;
        instance.resetting = true;
        instance.stopAll();
    }

    static void finishReset() {
        if (instance != null) instance.resetting = false;
    }

    private void stopAll() throws ReflectiveOperationException {
        ReflectiveOperationException failure = null;
        for (DirectionalWheel wheel : wheels.values()) {
            try {
                wheel.stop();
            } catch (ReflectiveOperationException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
        wheels.clear();
    }

    private List<?> mappings() throws ReflectiveOperationException {
        return (List<?>) configurations.invoke(config);
    }

    private Object firstWheel() throws ReflectiveOperationException {
        for (Object mapping : mappings()) if ((Integer) type.invoke(mapping) == 2) return mapping;
        return null;
    }

    private Object findWheel(int code) throws ReflectiveOperationException {
        for (Object mapping : mappings()) {
            if ((Integer) type.invoke(mapping) != 2) continue;
            for (Key key : keys(mapping)) if (key.code() == code) return mapping;
        }
        return null;
    }

    private List<Key> keys(Object mapping) throws ReflectiveOperationException {
        List<Key> result = new ArrayList<>();
        Object values = combine.invoke(mapping);
        if (values == null) return result;
        for (int i = 0; i < directions.length; i++) {
            Object key = directions[i].invoke(values);
            if (key != null) result.add(new Key((Integer) keyCode.invoke(key), Direction.values()[i],
                    new Point((Float) keyX.invoke(key), (Float) keyY.invoke(key))));
        }
        return result;
    }

    private Object nativePoint(Point value) throws ReflectiveOperationException {
        return point.newInstance(value.x(), value.y());
    }

    private Point position(Object value) {
        PointF point = (PointF) value;
        return new Point(point.x, point.y);
    }

    public Point convert(Point value) throws ReflectiveOperationException {
        return position(convert.invoke(touch, nativePoint(value)));
    }

    public Point jitter(Point value) throws ReflectiveOperationException {
        return position(jitter.invoke(touch, nativePoint(value)));
    }

    public float jitterOffset(float value) throws ReflectiveOperationException {
        return (Float) jitterOffset.invoke(touch, value);
    }

    public Object down(int pointer, Point value, int key) throws ReflectiveOperationException {
        down.invoke(touch, pointer, nativePoint(value), -1, false, key);
        for (Object contact : (List<?>) contacts.get(null)) {
            if ((Integer) contactCode.invoke(contact) == pointer && (Integer) contactTool.invoke(contact) == -1) {
                return contact;
            }
        }
        return null;
    }

    public boolean alive(Object contact) throws ReflectiveOperationException {
        if (contact == null) return false;
        for (Object value : (List<?>) contacts.get(null)) if (value == contact) return true;
        return false;
    }

    public void move(Object contact, Point value) throws ReflectiveOperationException {
        move.invoke(touch, nativePoint(value), contactId.invoke(contact), false);
    }

    public void up(Object contact, boolean force) throws ReflectiveOperationException {
        if (!force) {
            up.invoke(touch, contactCode.invoke(contact), contactPoint.invoke(contact), -1, false, null, null);
            return;
        }
        // The OEM wrapper drops UP outside game mode. Release only this exact owned contact.
        PointF value = (PointF) contactPoint.invoke(contact);
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, value.x, value.y, 0);
        event.setSource(0x1002);
        try {
            inject.invoke(inputService, event, (Integer) contactId.invoke(contact) + 11);
        } finally {
            event.recycle();
        }
        ((List<?>) contacts.get(null)).removeIf(valueInList -> valueInList == contact);
    }

    public void failed(Exception error) {
        xposed.log(Log.ERROR, TAG, "additional wheel input failed", error);
    }

    private void intercept(Method method, XposedInterface.Hooker hooker) {
        xposed.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(hooker);
    }

    private static Object singleton(Class<?> type) throws ReflectiveOperationException {
        Field field = type.getDeclaredField("a");
        if (field.getType() != type || !Modifier.isStatic(field.getModifiers())) {
            throw new NoSuchFieldException(type.getName() + ".a type changed");
        }
        field.setAccessible(true);
        return field.get(null);
    }

    private static Method method(Class<?> type, String name, Class<?>... args)
            throws ReflectiveOperationException {
        Method method = type.getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }

    private static final class Dispatch {
        boolean handled;
    }
}
