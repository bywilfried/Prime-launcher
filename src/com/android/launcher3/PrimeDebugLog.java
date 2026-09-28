package com.android.launcher3;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;

/** Small in-process diagnostic journal for Prime features. */
public final class PrimeDebugLog {

    private static final int MAX_ENTRIES = 300;
    private static final Deque<String> ENTRIES = new ArrayDeque<>();

    private PrimeDebugLog() {}

    public static void d(String tag, String message) {
        Log.d(tag, message);
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        synchronized (ENTRIES) {
            ENTRIES.addLast(timestamp + "  " + tag + "  " + message);
            while (ENTRIES.size() > MAX_ENTRIES) {
                ENTRIES.removeFirst();
            }
        }
    }

    public static String getText() {
        synchronized (ENTRIES) {
            if (ENTRIES.isEmpty()) return "No Prime diagnostic logs yet.";
            StringBuilder out = new StringBuilder();
            for (String entry : ENTRIES) {
                if (out.length() > 0) out.append('\n');
                out.append(entry);
            }
            return out.toString();
        }
    }

    public static int size() {
        synchronized (ENTRIES) {
            return ENTRIES.size();
        }
    }

    public static void clear() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
        }
    }
}
