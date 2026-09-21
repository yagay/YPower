package com.yagay.ypower.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.yagay.ypower.data.ProfileStore;
import com.yagay.ypower.model.AppProfile;
import com.yagay.ypower.root.EnhancementEngine;

public class AppDetailActivity extends AppCompatActivity {
    private String packageName;
    private AppProfile profile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        packageName = getIntent().getStringExtra("package");
        if (packageName == null || packageName.isBlank()) { finish(); return; }
        profile = ProfileStore.get(this).getProfile(packageName);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText(packageName);
        title.setTextSize(22);
        root.addView(title);

        CheckBox enabled = addCheck(root, "启用 YPower 增强", profile.enabled);
        enabled.setOnCheckedChangeListener((v, checked) -> { profile.enabled = checked; save(); });

        root.addView(section("无需目标 App Hook"));
        CheckBox doze = addCheck(root, "Doze 白名单", profile.dozeWhitelist);
        CheckBox bg = addCheck(root, "后台 AppOps 放宽", profile.backgroundOps);
        CheckBox standby = addCheck(root, "App Standby Active", profile.standbyActive);
        CheckBox data = addCheck(root, "后台数据白名单", profile.backgroundData);
        CheckBox grant = addCheck(root, "自动授予可正常 grant 的危险权限", profile.autoGrantDangerous);

        root.addView(section("目标进程兼容层（需要 LSPosed）"));
        CheckBox system = addCheck(root, "模拟 System App 身份（仅目标 App 进程看到）", profile.simulateSystemApp);
        CheckBox perm = addCheck(root, "模拟权限状态（默认位置权限；不等于真正 privileged 权限）", profile.simulatePermissions);
        CheckBox trace = addCheck(root, "Java/环境行为追踪", profile.traceJava || profile.traceEnvironment);

        Button apply = new Button(this);
        apply.setText("保存并应用增强");
        apply.setOnClickListener(v -> {
            profile.enabled = enabled.isChecked();
            profile.dozeWhitelist = doze.isChecked();
            profile.backgroundOps = bg.isChecked();
            profile.standbyActive = standby.isChecked();
            profile.backgroundData = data.isChecked();
            profile.autoGrantDangerous = grant.isChecked();
            profile.simulateSystemApp = system.isChecked();
            profile.simulatePermissions = perm.isChecked();
            profile.traceJava = trace.isChecked();
            profile.traceEnvironment = trace.isChecked();
            save();
            EnhancementEngine.applyAsync(this, profile, result -> runOnUiThread(() ->
                    Toast.makeText(this, result.summary(), Toast.LENGTH_LONG).show()));
        });
        root.addView(apply);

        Button diagnose = new Button(this);
        diagnose.setText("打开诊断中心");
        diagnose.setOnClickListener(v -> {
            Intent i = new Intent(this, DiagnosticActivity.class);
            i.putExtra("package", packageName);
            startActivity(i);
        });
        root.addView(diagnose);
        setContentView(scroll);
    }

    private void save() { ProfileStore.get(this).save(profile); }

    private CheckBox addCheck(LinearLayout root, String text, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(text);
        box.setChecked(checked);
        box.setPadding(0, dp(6), 0, dp(6));
        root.addView(box);
        return box;
    }

    private TextView section(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(18);
        v.setPadding(0, dp(18), 0, dp(4));
        return v;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
