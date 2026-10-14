package com.android.launcher3;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Persistent, bounded widget binding diagnostics; separate for each app package. */
public final class PrimeWidgetTrace {
    private static final String TAG = "PrimeWidgetTrace";
    private static final String FILE_NAME = "prime-widget-trace.txt";
    private static final String PREVIOUS_NAME = "prime-widget-trace.previous.txt";
    private static final long MAX_BYTES = 256 * 1024;
    private static final Object LOCK = new Object();

    private PrimeWidgetTrace() {}

    public static void d(Context context, String message) {
        write(context, "D", message);
    }

    public static void e(Context context, String message) {
        write(context, "E", message);
    }

    private static void write(Context context, String level, String message) {
        if ("E".equals(level)) Log.e(TAG, message);
        else Log.d(TAG, message);
        synchronized (LOCK) {
            try {
                File directory = context.getApplicationContext().getFilesDir();
                File current = new File(directory, FILE_NAME);
                if (current.length() >= MAX_BYTES) {
                    File previous = new File(directory, PREVIOUS_NAME);
                    if (previous.exists() && !previous.delete()) {
                        Log.w(TAG, "Cannot remove previous widget trace");
                    }
                    if (!current.renameTo(previous)) {
                        Log.w(TAG, "Cannot rotate widget trace");
                    }
                }
                String timestamp = new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(new Date());
                String line = timestamp + " " + level + " " + message + "\n";
                try (FileOutputStream out = new FileOutputStream(current, true)) {
                    out.write(line.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception ex) {
                Log.e(TAG, "Unable to persist widget trace", ex);
            }
        }
    }

    public static String exportText(Context context) {
        synchronized (LOCK) {
            StringBuilder result = new StringBuilder();
            File dir = context.getApplicationContext().getFilesDir();
            for (String name : new String[] {PREVIOUS_NAME, FILE_NAME}) {
                File file = new File(dir, name);
                if (!file.isFile()) continue;
                try {
                    result.append("\n--- ").append(name).append(" ---\n");
                    result.append(new String(java.nio.file.Files.readAllBytes(file.toPath()),
                            StandardCharsets.UTF_8));
                } catch (Exception ex) {
                    result.append("\nCannot read ").append(name).append(": ")
                            .append(ex.getMessage()).append("\n");
                }
            }
            if (result.length() == 0) return "\nNo persistent widget traces yet.\n";
            return result.toString();
        }
    }
}
