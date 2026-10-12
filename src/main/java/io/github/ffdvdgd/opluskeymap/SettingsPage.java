package io.github.ffdvdgd.opluskeymap;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.function.Consumer;

abstract class SettingsPage extends Activity {
    protected SettingsApp app;
    protected LinearLayout page;
    protected TextView status;
    private final ShortcutKeys shortcuts = new ShortcutKeys();
    private final Runnable refresh = () -> {
        status.setText(app.status());
        refreshPreferences();
    };

    @Override
    public void onCreate(Bundle state) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(state);
        app = (SettingsApp) getApplication();
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(16), dp(20), dp(12));
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(dp(20) + bars.left, dp(16) + bars.top,
                    dp(20) + bars.right, dp(12) + bars.bottom);
            return insets;
        });
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(12));
        if (!isTaskRoot()) {
            TextView back = text("‹", 32);
            back.setGravity(Gravity.CENTER);
            back.setContentDescription("返回");
            back.setFocusable(true);
            back.setBackgroundResource(android.R.drawable.list_selector_background);
            back.setOnClickListener(view -> finish());
            header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        TextView heading = text(getTitle().toString(), 24);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(header);
        status = text("", 14);
        status.setPadding(0, 0, 0, dp(16));
        page.addView(status);
        setContentView(page);
    }

    @Override
    protected void onStart() {
        super.onStart();
        app.observe(refresh);
        refresh.run();
    }

    @Override
    protected void onStop() {
        app.removeObserver(refresh);
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) shortcuts.clear();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return handleShortcut(event) || super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return (ShortcutKeys.mouseKey(event) != KeyEvent.KEYCODE_UNKNOWN && handleShortcut(event))
                || super.dispatchGenericMotionEvent(event);
    }

    private boolean handleShortcut(InputEvent event) {
        SharedPreferences prefs = app.preferences();
        ShortcutKeys.Action action = shortcuts.handle(event,
                prefs == null ? 0 : prefs.getInt(SettingsApp.RELEASE_KEY, 0),
                prefs == null ? 0 : prefs.getInt(SettingsApp.RESET_KEY, 0), () -> prefs != null);
        if (action == ShortcutKeys.Action.PASS) return false;
        if (action == ShortcutKeys.Action.CONSUME) return true;
        boolean reset = action == ShortcutKeys.Action.RESET;
        Consumer<Integer> completion = result -> {
            if (isDestroyed()) return;
            Toast.makeText(this, result == RESULT_OK ? (reset ? "已松开并复位所有触摸" : "已松开鼠标")
                    : result == RESULT_CANCELED ? "游戏助手未响应，请确认模块已启用。"
                    : "操作失败，请查看模块日志。", Toast.LENGTH_SHORT).show();
        };
        try {
            if (reset) app.resetTouches(completion);
            else app.releaseMouse(completion);
        } catch (RuntimeException error) {
            Toast.makeText(this, "无法发送请求，请重新打开模块后重试。", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    protected abstract void refreshPreferences();

    protected TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        return view;
    }

    protected int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
