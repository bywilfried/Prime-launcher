package com.android.launcher3;

import android.util.Log;
import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;

/** Small in-process diagnostic journal for Prime features. */
public final class PrimeDebugLog {

    private static final int MAX_ENTRIES = 1500;
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

    private static final String POPUP_PREFS = "prime_popup_diagnostic";
    private static final String POPUP_KEY = "trace";

    /** Persist popup traces so opening settings or a process restart cannot lose them. */
    public static void popup(Context context, String message) {
        d("PrimePopupTrace", message);
        if (context == null) return;
        android.content.SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(POPUP_PREFS, Context.MODE_PRIVATE);
        String previous = prefs.getString(POPUP_KEY, "");
        String next = previous + new SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                .format(new Date()) + "  PrimePopupTrace  " + message + "\n";
        if (next.length() > 30000) next = next.substring(next.length() - 30000);
        prefs.edit().putString(POPUP_KEY, next).apply();
    }

    public static String getPopupText(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(POPUP_PREFS, Context.MODE_PRIVATE)
                .getString(POPUP_KEY, "");
    }

    public static void clearPopup(Context context) {
        context.getApplicationContext()
                .getSharedPreferences(POPUP_PREFS, Context.MODE_PRIVATE)
                .edit().remove(POPUP_KEY).apply();
    }

    private static final String WALLPAPER_PREFS = "prime_wallpaper_diagnostic";
    private static final String WALLPAPER_KEY = "trace";

    /** Persist metadata-only wallpaper diagnostics across process restarts. */
    public static synchronized void wallpaper(Context context, String message) {
        d("PrimeWallpaperTrace", message);
        if (context == null) return;
        android.content.SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(WALLPAPER_PREFS, Context.MODE_PRIVATE);
        String previous = prefs.getString(WALLPAPER_KEY, "");
        String next = previous + new SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                .format(new Date()) + "  PrimeWallpaperTrace  " + message + "\n";
        if (next.length() > 30000) next = next.substring(next.length() - 30000);
        prefs.edit().putString(WALLPAPER_KEY, next).apply();
    }

    public static String getWallpaperText(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(WALLPAPER_PREFS, Context.MODE_PRIVATE)
                .getString(WALLPAPER_KEY, "");
    }

    public static void clearWallpaper(Context context) {
        context.getApplicationContext()
                .getSharedPreferences(WALLPAPER_PREFS, Context.MODE_PRIVATE)
                .edit().remove(WALLPAPER_KEY).apply();
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
