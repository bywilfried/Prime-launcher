package com.android.launcher3.model;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import com.android.launcher3.icons.BitmapInfo;
import android.net.Uri;
import android.util.Log;
import android.os.Process;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.data.AppInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Prime-managed HTTPS links, presented as independent drawer entries.
 * Browser installation, offline state and WebAPK creation remain browser-owned.
 */
public final class PrimeWebDrawerEntries {
    private PrimeWebDrawerEntries() {}
    private static final String TAG = "PrimeWebDrawer";

    public static void append(Context context, AllAppsList allApps) {
        final String serialized = context.getSharedPreferences("prime_web_apps", Context.MODE_PRIVATE)
                .getString("entries", "[]");
        try {
            final JSONArray entries = new JSONArray(serialized);
            for (int index = 0; index < entries.length(); index++) {
                final JSONObject entry = entries.optJSONObject(index);
                if (entry == null) continue;
                final String name = entry.optString("name").trim();
                final String url = entry.optString("url");
                final Uri uri = Uri.parse(url);
                if (name.isEmpty() || !"https".equalsIgnoreCase(uri.getScheme())
                        || uri.getHost() == null || uri.getHost().isEmpty()
                        || uri.getUserInfo() != null || uri.getPort() == 0
                        || uri.getPort() > 65535) continue;

                try {
                final String identifier = sha256(url);
                final ComponentName component = new ComponentName(
                        context.getPackageName(),
                        context.getPackageName() + ".prime.webapps.Web_" + identifier);
                if (allApps.findAppInfo(component, Process.myUserHandle()) != null) continue;
                final Intent launch = new Intent(Intent.ACTION_VIEW, uri)
                        .addCategory(Intent.CATEGORY_BROWSABLE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                final AppInfo app = new AppInfo(component, name, Process.myUserHandle(), launch);
                app.container = LauncherSettings.Favorites.CONTAINER_ALL_APPS;
                app.bitmap = makeIcon(context, url, name, identifier);
                app.sectionName = name.substring(0, 1).toUpperCase(java.util.Locale.getDefault());
                allApps.data.add(app);
                } catch (Exception error) {
                    Log.w(TAG, "Skipping invalid Web drawer entry", error);
                }
            }
        } catch (Exception error) {
            Log.w(TAG, "Unable to parse saved Web drawer entries", error);
        }
    }

    /** Deterministic, offline-capable icon while no browser icon is available. */
    private static BitmapInfo makeIcon(Context context, String url, String name, String identifier) {
        Bitmap siteIcon = PrimeWebIconCache.load(context, url);
        if (siteIcon != null) return BitmapInfo.of(siteIcon, Color.GRAY);
        final int size = 144;
        final Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int hue = Integer.parseInt(identifier.substring(0, 2), 16) * 360 / 256;
        final float[] hsv = {hue, 0.48f, 0.62f};
        paint.setColor(Color.HSVToColor(hsv));
        canvas.drawRoundRect(0, 0, size, size, 28, 28, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        paint.setTextSize(76);
        final String letter = name.substring(0, 1).toUpperCase(java.util.Locale.getDefault());
        canvas.drawText(letter, size / 2f, size / 2f - (paint.ascent() + paint.descent()) / 2f, paint);
        return BitmapInfo.of(bitmap, Color.HSVToColor(hsv));
    }

    private static String sha256(String value) throws Exception {
        final byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        final StringBuilder result = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            result.append(Character.forDigit((digest[i] >> 4) & 15, 16));
            result.append(Character.forDigit(digest[i] & 15, 16));
        }
        return result.toString();
    }
}
