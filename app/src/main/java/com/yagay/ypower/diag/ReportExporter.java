package com.yagay.ypower.diag;

import android.content.Context;

import com.yagay.ypower.model.DiagnosticReport;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ReportExporter {
    private ReportExporter() {}

    public static File export(Context context, DiagnosticReport report) throws Exception {
        File dir = new File(context.getExternalFilesDir(null), "diagnostics");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create report directory");
        String ts = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date(report.createdAt));
        File file = new File(dir, "YPower-" + report.packageName.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + ts + ".json");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(report.toJson().toString(2).getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
}
