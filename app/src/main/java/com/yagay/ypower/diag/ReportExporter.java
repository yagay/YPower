package com.yagay.ypower.diag;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import com.yagay.ypower.model.DiagnosticReport;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ReportExporter {
    private ReportExporter() {}

    public static String suggestedFileName(DiagnosticReport report) {
        String ts = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)
                .format(new Date(report.createdAt));
        String pkg = report.packageName.replaceAll("[^A-Za-z0-9._-]", "_");
        return "YPower-" + pkg + "-" + ts + ".json";
    }

    public static Uri exportToDownloads(Context context, DiagnosticReport report) throws Exception {
        ContentResolver resolver = context.getContentResolver();

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, suggestedFileName(report));
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
        values.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/YPower"
        );
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IllegalStateException("Unable to create Downloads document");
        }

        boolean success = false;
        try {
            writeToUri(context, uri, report);
            success = true;
        } finally {
            if (success) {
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(uri, ready, null, null);
            } else {
                resolver.delete(uri, null, null);
            }
        }
        return uri;
    }

    public static void writeToUri(Context context, Uri uri, DiagnosticReport report) throws Exception {
        byte[] data = report.toJson().toString(2).getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IllegalStateException("Unable to open output stream");
            out.write(data);
            out.flush();
        }
    }
}
