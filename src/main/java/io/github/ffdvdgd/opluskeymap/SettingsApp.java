package io.github.ffdvdgd.opluskeymap;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class SettingsApp extends Application {
    static final String GROUP = "mouse_move";
    static final String GAMES = "games";
    static final String RESET_TOUCHES = "io.github.ffdvdgd.opluskeymap.RESET_TOUCHES";
    static final String RESET_PERMISSION = "io.github.ffdvdgd.opluskeymap.permission.RESET_TOUCHES";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final Set<Runnable> observers = new CopyOnWriteArraySet<>();
    private volatile XposedService service;
    private volatile SharedPreferences preferences;
    private volatile String status = "未连接 LSPosed 服务。请启用模块后重新打开设置。";

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService value) {
                service = value;
                try {
                    if ((value.getFrameworkProperties() & XposedService.PROP_CAP_REMOTE) != 0) {
                        preferences = value.getRemotePreferences(GROUP);
                        status = "已连接 " + value.getFrameworkName();
                    } else {
                        preferences = null;
                        status = "当前框架不支持保存设置。";
                    }
                } catch (RuntimeException error) {
                    preferences = null;
                    status = "无法读取设置。请结束并重新打开本模块。";
                }
                notifyObservers();
            }

            @Override
            public void onServiceDied(XposedService value) {
                if (service == value) {
                    preferences = null;
                    service = null;
                    status = "LSPosed 服务已断开，暂时无法保存设置。";
                    notifyObservers();
                }
            }
        });
    }

    SharedPreferences preferences() {
        return preferences;
    }

    String status() {
        return status;
    }

    void observe(Runnable observer) {
        observers.add(observer);
    }

    void removeObserver(Runnable observer) {
        observers.remove(observer);
    }

    void resetTouches(Consumer<Integer> completion) {
        inputAction(RESET_TOUCHES, completion);
    }

    private void inputAction(String action, Consumer<Integer> completion) {
        // The default result means no live hook answered, not a successful reset.
        sendOrderedBroadcast(new Intent(action).setPackage("com.oplus.games"), null,
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        completion.accept(getResultCode());
                    }
                }, main, Activity.RESULT_CANCELED, null, null);
    }

    void save(Set<String> games, Consumer<Boolean> completion) {
        save(editor -> editor.putStringSet(GAMES, games), completion);
    }

    private void save(Consumer<SharedPreferences.Editor> change, Consumer<Boolean> completion) {
        SharedPreferences prefs = preferences;
        writer.execute(() -> {
            boolean saved = false;
            try {
                if (prefs != null) {
                    SharedPreferences.Editor editor = prefs.edit();
                    change.accept(editor);
                    saved = editor.commit();
                }
            } catch (RuntimeException ignored) {
                // Report failure instead of claiming that an asynchronous write succeeded.
            }
            if (!saved) {
                preferences = null;
                status = "保存失败。请结束并重新打开本模块。";
            }
            boolean result = saved;
            main.post(() -> completion.accept(result));
        });
    }

    private void notifyObservers() {
        main.post(() -> {
            for (Runnable observer : observers) observer.run();
        });
    }
}
