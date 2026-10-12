package io.github.ffdvdgd.opluskeymap;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Collections;

public final class SettingsActivity extends SettingsPage {
    private TextView gamesSummary;
    private LinearLayout resetRow;
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
        TextView recovery = text("故障恢复", 16);
        recovery.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        recovery.setPadding(0, dp(24), 0, dp(8));
        sections.addView(recovery);
        resetRow = setting("强制松开并复位所有触摸", text("触摸卡住时使用，不会更改设置", 14));
        resetRow.setOnClickListener(view -> resetTouches());
        sections.addView(resetRow);
    }

    @Override
    protected void refreshPreferences() {
        SharedPreferences prefs = app.preferences();
        gamesSummary.setText(prefs == null ? "设置暂不可读取"
                : "已选择 " + prefs.getStringSet(SettingsApp.GAMES, Collections.emptySet()).size() + " 个游戏");
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
}
