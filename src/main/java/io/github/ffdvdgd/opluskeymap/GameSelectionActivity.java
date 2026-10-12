package io.github.ffdvdgd.opluskeymap;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

public final class GameSelectionActivity extends SettingsPage {
    private TextView count;
    private LinearLayout rows;
    private EditText search;
    private final Set<String> selected = new HashSet<>();
    private final List<CheckBox> choices = new ArrayList<>();
    private boolean saving;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("搜索应用名或包名");
        page.addView(search);
        count = text("", 14);
        count.setPadding(0, dp(12), 0, dp(8));
        page.addView(count);
        ScrollView scroll = new ScrollView(this);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override public void afterTextChanged(Editable value) {}
        });
    }

    @Override
    protected void refreshPreferences() {
        SharedPreferences prefs = app.preferences();
        selected.clear();
        if (prefs != null) selected.addAll(prefs.getStringSet(SettingsApp.GAMES, Collections.emptySet()));
        TreeMap<String, String> labels = new TreeMap<>();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : getPackageManager().queryIntentActivities(launcher, 0)) {
            String pkg = info.activityInfo.packageName;
            if (!getPackageName().equals(pkg)) labels.put(pkg, info.loadLabel(getPackageManager()).toString());
        }
        // Keep previously selected packages visible even if their launcher was removed.
        for (String pkg : selected) if (!labels.containsKey(pkg)) labels.put(pkg, pkg);
        choices.clear();
        for (String pkg : labels.keySet()) {
            CheckBox choice = new CheckBox(this);
            choice.setText(labels.get(pkg) + "\n" + pkg);
            choice.setTextSize(14);
            choice.setPadding(dp(4), dp(8), dp(4), dp(8));
            Drawable icon;
            try {
                icon = getPackageManager().getApplicationIcon(pkg);
            } catch (PackageManager.NameNotFoundException missing) {
                icon = getPackageManager().getDefaultActivityIcon();
            }
            icon.setBounds(0, 0, dp(40), dp(40));
            choice.setCompoundDrawablesRelative(icon, null, null, null);
            choice.setCompoundDrawablePadding(dp(12));
            choice.setChecked(selected.contains(pkg));
            choice.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selected.add(pkg);
                else selected.remove(pkg);
                saving = true;
                updateControls();
                status.setText("正在保存…");
                app.save(new HashSet<>(selected), saved -> {
                    saving = false;
                    if (isDestroyed()) return;
                    status.setText(saved ? app.status() : "保存失败。请结束并重新打开本模块后确认名单。");
                    if (saved) orderChoices();
                    updateControls();
                });
            });
            choices.add(choice);
        }
        orderChoices();
        filter(search.getText().toString());
        updateControls();
    }

    private void orderChoices() {
        choices.sort((a, b) -> {
            int checked = Boolean.compare(b.isChecked(), a.isChecked());
            return checked != 0 ? checked : a.getText().toString().compareToIgnoreCase(b.getText().toString());
        });
        rows.removeAllViews();
        for (CheckBox choice : choices) rows.addView(choice);
    }

    private void filter(String query) {
        String value = query.trim().toLowerCase(Locale.ROOT);
        for (CheckBox choice : choices) {
            choice.setVisibility(choice.getText().toString().toLowerCase(Locale.ROOT).contains(value)
                    ? View.VISIBLE : View.GONE);
        }
    }

    private void updateControls() {
        boolean writable = app.preferences() != null && !saving;
        for (CheckBox choice : choices) choice.setEnabled(writable);
        count.setText(app.preferences() == null ? "名单暂不可读取" : "已选择 " + selected.size() + " 个应用");
    }
}
