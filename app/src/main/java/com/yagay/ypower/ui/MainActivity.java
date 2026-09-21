package com.yagay.ypower.ui;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.yagay.ypower.data.ProfileStore;
import com.yagay.ypower.root.RootShell;
import com.yagay.ypower.xposed.XposedBridgeManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private LinearLayout list;
    private EditText search;
    private final List<ApplicationInfo> apps = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadApps("");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (list != null) loadApps(search == null ? "" : search.getText().toString());
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = new TextView(this);
        title.setText("应用增强 · YPower");
        title.setTextSize(24);
        root.addView(title);

        TextView status = new TextView(this);
        status.setText("Root: " + (RootShell.isRootAvailable() ? "已连接" : "未授权")
                + "    LSPosed Service: " + (XposedBridgeManager.isReady() ? "已连接" : "未连接"));
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        search = new EditText(this);
        search.setHint("搜索应用或包名");
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));

        Button refresh = new Button(this);
        refresh.setText("搜索 / 刷新");
        refresh.setOnClickListener(v -> loadApps(search.getText().toString()));
        root.addView(refresh);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @SuppressWarnings("deprecation")
    private void loadApps(String query) {
        PackageManager pm = getPackageManager();
        if (apps.isEmpty()) {
            apps.addAll(pm.getInstalledApplications(0));
            apps.removeIf(a -> getPackageName().equals(a.packageName));
            apps.sort(Comparator.comparing(a -> String.valueOf(pm.getApplicationLabel(a)), String.CASE_INSENSITIVE_ORDER));
        }
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        list.removeAllViews();
        for (ApplicationInfo app : apps) {
            String label = String.valueOf(pm.getApplicationLabel(app));
            if (!q.isEmpty() && !label.toLowerCase(Locale.ROOT).contains(q) && !app.packageName.toLowerCase(Locale.ROOT).contains(q)) continue;
            addRow(label, app.packageName);
        }
    }

    private void addRow(String label, String packageName) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));

        CheckBox enabled = new CheckBox(this);
        enabled.setChecked(ProfileStore.get(this).getProfile(packageName).enabled);
        enabled.setOnCheckedChangeListener((buttonView, isChecked) -> ProfileStore.get(this).setEnabled(packageName, isChecked));
        row.addView(enabled);

        TextView text = new TextView(this);
        text.setText(label + "\n" + packageName);
        text.setTextSize(16);
        text.setOnClickListener(v -> openDetails(packageName));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));

        Button detail = new Button(this);
        detail.setText("设置");
        detail.setOnClickListener(v -> openDetails(packageName));
        row.addView(detail);
        list.addView(row);
    }

    private void openDetails(String packageName) {
        Intent i = new Intent(this, AppDetailActivity.class);
        i.putExtra("package", packageName);
        startActivity(i);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
