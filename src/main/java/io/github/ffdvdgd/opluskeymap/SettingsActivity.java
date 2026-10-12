package io.github.ffdvdgd.opluskeymap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Collections;

public final class SettingsActivity extends SettingsPage {
    private TextView gamesSummary;
    private TextView releaseSummary;
    private TextView resetSummary;
    private LinearLayout releaseRow;
    private LinearLayout resetRow;
    private LinearLayout resetKeyRow;
    private boolean saving;
    private boolean resetting;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout sections = new LinearLayout(this);
        sections.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(sections);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView category = text("鼠标移动", 16);
        category.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        category.setPadding(0, dp(12), 0, dp(8));
        sections.addView(category);
        gamesSummary = text("", 14);
        LinearLayout gamesRow = setting("保持鼠标按下", gamesSummary);
        gamesRow.setOnClickListener(view -> startActivity(new Intent(this, GameSelectionActivity.class)));
        sections.addView(gamesRow);
        releaseSummary = text("", 14);
        releaseRow = setting("手动松开按键", releaseSummary);
        releaseRow.setOnClickListener(view -> chooseShortcut(SettingsApp.RELEASE_KEY, "手动松开按键"));
        sections.addView(releaseRow);
        TextView recovery = text("故障恢复", 16);
        recovery.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        recovery.setPadding(0, dp(24), 0, dp(8));
        sections.addView(recovery);
        resetRow = setting("强制松开并复位所有触摸", text("触摸卡住时使用，不会更改设置", 14));
        resetRow.setOnClickListener(view -> resetTouches());
        sections.addView(resetRow);
        resetSummary = text("", 14);
        resetKeyRow = setting("强制复位按键", resetSummary);
        resetKeyRow.setOnClickListener(view -> chooseShortcut(SettingsApp.RESET_KEY, "强制复位按键"));
        sections.addView(resetKeyRow);
    }

    @Override
    protected void refreshPreferences() {
        SharedPreferences prefs = app.preferences();
        gamesSummary.setText(prefs == null ? "设置暂不可读取"
                : "已选择 " + prefs.getStringSet(SettingsApp.GAMES, Collections.emptySet()).size() + " 个游戏");
        releaseSummary.setText(prefs == null ? "设置暂不可读取"
                : keyName(prefs.getInt(SettingsApp.RELEASE_KEY, KeyEvent.KEYCODE_UNKNOWN)));
        resetSummary.setText(prefs == null ? "设置暂不可读取"
                : keyName(prefs.getInt(SettingsApp.RESET_KEY, KeyEvent.KEYCODE_UNKNOWN)));
        releaseRow.setEnabled(prefs != null && !saving);
        resetKeyRow.setEnabled(prefs != null && !saving);
        resetRow.setEnabled(!resetting);
    }

    private void resetTouches() {
        resetting = true;
        resetRow.setEnabled(false);
        status.setText("正在复位…");
        try {
            app.resetTouches(result -> {
                resetting = false;
                if (isDestroyed()) return;
                resetRow.setEnabled(true);
                status.setText(result == Activity.RESULT_OK ? "已松开并复位所有触摸"
                        : result == Activity.RESULT_CANCELED
                        ? "游戏助手未响应，请先进入游戏并确认模块已启用。"
                        : "复位失败，请查看模块日志。");
            });
        } catch (RuntimeException error) {
            resetting = false;
            resetRow.setEnabled(true);
            status.setText("无法发送复位请求，请重新打开模块后重试。");
        }
    }

    private void chooseShortcut(String key, String title) {
        int[] captured = {KeyEvent.KEYCODE_UNKNOWN};
        AlertDialog dialog = new AlertDialog(this) {
            @Override
            public boolean dispatchGenericMotionEvent(MotionEvent event) {
                int code = ShortcutKeys.mouseKey(event);
                if (code != KeyEvent.KEYCODE_UNKNOWN) {
                    if (!saving && captured[0] == KeyEvent.KEYCODE_UNKNOWN
                            && event.getActionMasked() == MotionEvent.ACTION_BUTTON_PRESS) {
                        captured[0] = code;
                        saveShortcut(key, code);
                    }
                    if (captured[0] == code && event.getActionMasked() == MotionEvent.ACTION_BUTTON_RELEASE) {
                        dismiss();
                    }
                    return true;
                }
                return super.dispatchGenericMotionEvent(event);
            }
        };
        dialog.setTitle("设置" + title);
        dialog.setMessage("按下要绑定的按键。绑定按键在模块界面和游戏中仅供模块使用。");
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, "取消", (ignored, which) -> {});
        dialog.setButton(DialogInterface.BUTTON_NEUTRAL, "清除",
                (ignored, which) -> saveShortcut(key, KeyEvent.KEYCODE_UNKNOWN));
        dialog.setOnKeyListener((ignored, code, event) -> {
            if (!saving && captured[0] == KeyEvent.KEYCODE_UNKNOWN && code != KeyEvent.KEYCODE_UNKNOWN
                    && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                captured[0] = code;
                saveShortcut(key, code);
            }
            // Keep both mouse/key DOWN events in this window while recording;
            // dismissing on DOWN could run the new binding in the parent page.
            if (captured[0] == code && event.getAction() == KeyEvent.ACTION_UP) dialog.dismiss();
            return true;
        });
        dialog.show();
    }

    private void saveShortcut(String key, int code) {
        saving = true;
        releaseRow.setEnabled(false);
        resetKeyRow.setEnabled(false);
        status.setText("正在保存…");
        app.saveShortcut(key, code, saved -> {
            saving = false;
            if (isDestroyed()) return;
            status.setText(app.status());
            refreshPreferences();
        });
    }

    private LinearLayout setting(String title, TextView summary) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(16), dp(12), dp(16));
        row.setFocusable(true);
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.addView(text(title, 18));
        summary.setAlpha(0.7f);
        summary.setPadding(0, dp(4), 0, 0);
        row.addView(summary);
        return row;
    }

    private String keyName(int code) {
        return code == KeyEvent.KEYCODE_UNKNOWN ? "未设置"
                : KeyEvent.keyCodeToString(code).replace("KEYCODE_", "");
    }
}
