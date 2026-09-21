package com.yagay.ypower.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.yagay.ypower.diag.DiagnosticEngine;
import com.yagay.ypower.diag.ReportExporter;
import com.yagay.ypower.model.DiagnosticLevel;
import com.yagay.ypower.model.DiagnosticReport;

import java.io.File;

public class DiagnosticActivity extends AppCompatActivity {
    private String packageName;
    private Spinner levelSpinner;
    private Spinner viewSpinner;
    private TextView output;
    private DiagnosticReport lastReport;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        packageName = getIntent().getStringExtra("package");
        if (packageName == null || packageName.isBlank()) { finish(); return; }
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = new TextView(this);
        title.setText("诊断 · " + packageName);
        title.setTextSize(21);
        root.addView(title);

        levelSpinner = new Spinner(this);
        levelSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"快速", "标准", "深度"}));
        root.addView(levelSpinner);

        viewSpinner = new Spinner(this);
        viewSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"简要", "详细", "原始"}));
        root.addView(viewSpinner);

        LinearLayout buttons = new LinearLayout(this);
        Button run = new Button(this);
        run.setText("开始诊断");
        run.setOnClickListener(v -> startDiagnostic());
        buttons.addView(run, new LinearLayout.LayoutParams(0, -2, 1));

        Button show = new Button(this);
        show.setText("切换结果");
        show.setOnClickListener(v -> render());
        buttons.addView(show, new LinearLayout.LayoutParams(0, -2, 1));

        Button export = new Button(this);
        export.setText("导出 JSON");
        export.setOnClickListener(v -> exportReport());
        buttons.addView(export, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(buttons);

        ScrollView scroll = new ScrollView(this);
        output = new TextView(this);
        output.setTextIsSelectable(true);
        output.setText("选择诊断级别后开始。\n三个诊断级别都支持简要、详细和原始结果。");
        output.setPadding(0, dp(12), 0, dp(24));
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void startDiagnostic() {
        DiagnosticLevel level = DiagnosticLevel.values()[levelSpinner.getSelectedItemPosition()];
        output.setText("正在采集诊断信息…");
        DiagnosticEngine.runAsync(this, packageName, level, report -> runOnUiThread(() -> {
            lastReport = report;
            render();
        }));
    }

    private void render() {
        if (lastReport == null) return;
        int mode = viewSpinner.getSelectedItemPosition();
        output.setText(mode == 0 ? lastReport.simpleText() : mode == 1 ? lastReport.detailedText() : lastReport.rawText());
    }

    private void exportReport() {
        if (lastReport == null) { Toast.makeText(this, "请先运行诊断", Toast.LENGTH_SHORT).show(); return; }
        try {
            File file = ReportExporter.export(this, lastReport);
            Toast.makeText(this, "已导出：" + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "导出失败：" + e, Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
