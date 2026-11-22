package com.android.launcher3.model;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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
                        || uri.getUserInfo() != null) continue;

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
                app.sectionName = name.substring(0, 1).toUpperCase(java.util.Locale.getDefault());
                allApps.data.add(app);
            }
        } catch (Exception ignored) {
            // Malformed persisted entries must never prevent the regular drawer from loading.
        }
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
