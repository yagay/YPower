package com.yagay.ypower.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.yagay.ypower.data.ProfileStore;
import com.yagay.ypower.data.RecommendedAppRegistry;
import com.yagay.ypower.model.AppProfile;
import com.yagay.ypower.model.RecommendedAppPreset;
import com.yagay.ypower.root.EnhancementEngine;
import com.yagay.ypower.xposed.XposedBridgeManager;

public class RecommendedAppsActivity extends AppCompatActivity {
    private LinearLayout list;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        populate();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = new TextView(this);
        title.setText("推荐应用");
        title.setTextSize(24);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("只显示已安装并且 YPower 有明确推荐规则的应用。\n"
                + "一键应用会同时保存推荐 Hook、启用 YPower，并请求加入 LSPosed Scope。\n"
                + "LSPosed Service：" + (XposedBridgeManager.isReady() ? "已连接" : "未连接，配置会先保存"));
        hint.setPadding(0, dp(8), 0, dp(12));
        root.addView(hint);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @SuppressWarnings("deprecation")
    private void populate() {
        list.removeAllViews();
        int count = 0;
        PackageManager pm = getPackageManager();

        for (RecommendedAppPreset preset : RecommendedAppRegistry.BUILTIN) {
            try {
                pm.getPackageInfo(preset.packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            count++;
            addPreset(preset);
        }

        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText("当前已安装应用中暂时没有命中内置推荐规则。");
            empty.setPadding(0, dp(24), 0, 0);
            list.addView(empty);
        }
    }

    private void addPreset(RecommendedAppPreset preset) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView name = new TextView(this);
        name.setText(preset.displayName + "\n" + preset.packageName);
        name.setTextSize(18);
        card.addView(name);

        TextView reason = new TextView(this);
        reason.setText("推荐原因：" + preset.reason + "\n推荐 Hook：" + preset.hookSummary());
        reason.setPadding(0, dp(6), 0, dp(8));
        card.addView(reason);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);

        Button apply = new Button(this);
        apply.setText("一键推荐 + LSPosed");
        apply.setOnClickListener(v -> applyPreset(preset));
        buttons.addView(apply, new LinearLayout.LayoutParams(0, -2, 1));

        Button detail = new Button(this);
        detail.setText("设置");
        detail.setOnClickListener(v -> {
            Intent i = new Intent(this, AppDetailActivity.class);
            i.putExtra("package", preset.packageName);
            startActivity(i);
        });
        buttons.addView(detail);

        card.addView(buttons);
        list.addView(card);
    }

    private void applyPreset(RecommendedAppPreset preset) {
        AppProfile profile = ProfileStore.get(this).applyRecommendedPreset(preset);
        EnhancementEngine.applyAsync(this, profile, result -> runOnUiThread(() -> {
            String scope = XposedBridgeManager.isReady()
                    ? "已请求同步到 LSPosed Scope"
                    : "LSPosed 未连接；配置已保存，连接后会再次请求 Scope";
            Toast.makeText(
                    this,
                    preset.displayName + "：推荐配置已应用\n" + scope + "\n" + result.summary(),
                    Toast.LENGTH_LONG
            ).show();
            populate();
        }));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
