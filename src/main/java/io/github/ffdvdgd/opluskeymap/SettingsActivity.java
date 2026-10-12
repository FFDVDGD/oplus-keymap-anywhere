package io.github.ffdvdgd.opluskeymap;

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
    }

    @Override
    protected void refreshPreferences() {
        SharedPreferences prefs = app.preferences();
        gamesSummary.setText(prefs == null ? "设置暂不可读取"
                : "已选择 " + prefs.getStringSet(SettingsApp.GAMES, Collections.emptySet()).size() + " 个游戏");
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
