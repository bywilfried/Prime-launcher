package com.android.launcher3.model;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Process;
import android.util.Base64;
import android.util.Log;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.shortcuts.ShortcutKey;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Drawer copies of launcher-owned shortcuts; never replace native launch intents with URLs. */
public final class PrimePinnedDrawerShortcuts {
    private static final String TAG = "PrimePinnedShortcuts";
    private static final String PREFS = "prime_pinned_drawer_shortcuts";
    private static final String ENTRIES = "entries";
    public static final String ACTION_PINNED = "app.lawnchair.prime.LAUNCH_PINNED_SHORTCUT";
    private static final String EXTRA_PACKAGE = "prime_shortcut_package";
    private static final String EXTRA_ID = "prime_shortcut_id";

    private PrimePinnedDrawerShortcuts() {}

    private static JSONArray read(Context context) {
        try {
            return new JSONArray(context.getSharedPreferences(PREFS, 0).getString(ENTRIES, "[]"));
        } catch (Exception error) {
            Log.w(TAG, "Invalid stored shortcuts", error);
            return new JSONArray();
        }
    }

    private static void save(Context context, JSONArray entries) {
        context.getSharedPreferences(PREFS, 0).edit().putString(ENTRIES, entries.toString()).apply();
    }

    private static String key(String kind, String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((kind + ":" + value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                result.append(Character.forDigit((bytes[i] >>> 4) & 15, 16));
                result.append(Character.forDigit(bytes[i] & 15, 16));
            }
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    public static boolean isEligible(Context context, ItemInfo item) {
        if (!(item instanceof WorkspaceItemInfo)
                || item.getIntent() == null || !Process.myUserHandle().equals(item.user)) {
            return false;
        }
        if (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT
                || item.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
            return true;
        }
        if (item.itemType != LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) return false;

        // Browser-created home icons can be stored as ITEM_TYPE_APPLICATION,
        // even though they are not regular installed apps. A standard launcher
        // activity must NOT be duplicated in the drawer.
        Intent intent = item.getIntent();
        if (!Intent.ACTION_MAIN.equals(intent.getAction())
                || !intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            return true;
        }
        // Some browsers wrap the web launch in an ACTION_MAIN intent, carrying
        // the actual URL / app identity in extras or data.
        if (intent.getData() != null || (intent.getExtras() != null
                && !intent.getExtras().isEmpty())) return true;

        // Brave and other browsers sometimes expose a home PWA as an
        // ACTION_MAIN/LAUNCHER intent with no URL or extras. In that case
        // the icon label differs from the browser application's own label.
        String pkg = item.getTargetPackage();
        if (pkg == null || item.title == null) return false;
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.content.pm.ApplicationInfo app = pm.getApplicationInfo(pkg, 0);
            String installedLabel = pm.getApplicationLabel(app).toString();
            if (installedLabel.equalsIgnoreCase(item.title.toString().trim())) return false;
            String lower = pkg.toLowerCase(java.util.Locale.ROOT);
            return lower.contains("browser") || lower.contains("firefox")
                    || lower.contains("vivaldi") || lower.contains("opera")
                    || lower.contains("chrome") || lower.contains("chromium")
                    || lower.contains("edge") || lower.contains("samsung.internet");
        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
            return false;
        }
    }

    private static String identity(Context context, ItemInfo item) {
        if (!isEligible(context, item)) return null;
        if (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
            ShortcutKey shortcut = ShortcutKey.fromItemInfo(item);
            if (shortcut.getId() == null || shortcut.getPackageName() == null) return null;
            return key("pinned", shortcut.getPackageName() + ":" + shortcut.getId());
        }
        return key("legacy", item.getIntent().toUri(Intent.URI_INTENT_SCHEME));
    }

    public static boolean contains(Context context, ItemInfo item) {
        String id = identity(context, item);
        if (id == null) return false;
        JSONArray entries = read(context);
        for (int i = 0; i < entries.length(); i++) {
            if (id.equals(entries.optJSONObject(i) == null ? null
                    : entries.optJSONObject(i).optString("id"))) return true;
        }
        return false;
    }

    public static boolean add(Context context, ItemInfo item) {
        String id = identity(context, item);
        if (id == null) return false;
        if (contains(context, item)) return true;
        try {
            JSONObject entry = new JSONObject();
            entry.put("id", id);
            entry.put("title", String.valueOf(item.title));
            if (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                ShortcutKey shortcut = ShortcutKey.fromItemInfo(item);
                entry.put("kind", "pinned");
                entry.put("package", shortcut.getPackageName());
                entry.put("shortcutId", shortcut.getId());
                // Keep the shortcut pinned when its home-screen icon is later removed.
                // pinShortcuts replaces this launcher's pinned set for the package,
                // so preserve every currently pinned shortcut first.
                LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
                LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery()
                        .setPackage(shortcut.getPackageName())
                        .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
                java.util.List<android.content.pm.ShortcutInfo> pinned =
                        launcherApps.getShortcuts(query, item.user);
                java.util.ArrayList<String> ids = new java.util.ArrayList<>();
                if (pinned != null) {
                    for (android.content.pm.ShortcutInfo existing : pinned) {
                        if (!ids.contains(existing.getId())) ids.add(existing.getId());
                    }
                }
                if (!ids.contains(shortcut.getId())) ids.add(shortcut.getId());
                launcherApps.pinShortcuts(shortcut.getPackageName(), ids, item.user);
            } else {
                entry.put("kind", "legacy");
                entry.put("intent", item.getIntent().toUri(Intent.URI_INTENT_SCHEME));
            }
            if (item instanceof WorkspaceItemInfo) {
                Bitmap icon = ((WorkspaceItemInfo) item).bitmap.icon;
                if (icon != null) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    if (icon.compress(Bitmap.CompressFormat.PNG, 100, bytes)
                            && bytes.size() < 256 * 1024) {
                        entry.put("icon", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP));
                    }
                }
            }
            JSONArray entries = read(context);
            entries.put(entry);
            save(context, entries);
            return true;
        } catch (Exception error) {
            Log.w(TAG, "Unable to save shortcut", error);
            return false;
        }
    }

    public static boolean remove(Context context, ItemInfo item) {
        String id = identity(context, item);
        if (id == null && item instanceof AppInfo) {
            ComponentName component = ((AppInfo) item).componentName;
            if (component != null && component.getClassName().startsWith(
                    context.getPackageName() + ".prime.pinned.Shortcut_")) {
                id = component.getClassName().substring(
                        (context.getPackageName() + ".prime.pinned.Shortcut_").length());
            }
        }
        if (id == null) return false;
        JSONArray old = read(context);
        JSONArray updated = new JSONArray();
        boolean found = false;
        for (int i = 0; i < old.length(); i++) {
            JSONObject entry = old.optJSONObject(i);
            if (entry == null) continue;
            if (id.equals(entry.optString("id"))) found = true;
            else updated.put(entry);
        }
        if (found) save(context, updated);
        return found;
    }

    public static boolean isDrawerEntry(Context context, ItemInfo item) {
        if (!(item instanceof AppInfo)) return false;
        ComponentName component = ((AppInfo) item).componentName;
        return component != null && component.getPackageName().equals(context.getPackageName())
                && component.getClassName().startsWith(
                context.getPackageName() + ".prime.pinned.Shortcut_");
    }

    public static void append(Context context, AllAppsList allApps) {
        JSONArray entries = read(context);
        for (int i = 0; i < entries.length(); i++) {
            try {
                JSONObject entry = entries.optJSONObject(i);
                if (entry == null) continue;
                String id = entry.getString("id");
                String title = entry.getString("title");
                Intent launch;
                if ("pinned".equals(entry.optString("kind"))) {
                    launch = new Intent(ACTION_PINNED)
                            .putExtra(EXTRA_PACKAGE, entry.getString("package"))
                            .putExtra(EXTRA_ID, entry.getString("shortcutId"));
                } else {
                    launch = Intent.parseUri(entry.getString("intent"), Intent.URI_INTENT_SCHEME);
                }
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ComponentName component = new ComponentName(context.getPackageName(),
                        context.getPackageName() + ".prime.pinned.Shortcut_" + id);
                AppInfo app = new AppInfo(component, title, Process.myUserHandle(), launch);
                app.itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
                app.container = LauncherSettings.Favorites.CONTAINER_ALL_APPS;
                String encoded = entry.optString("icon");
                if (!encoded.isEmpty()) {
                    byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
                    Bitmap icon = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (icon != null) app.bitmap = BitmapInfo.of(icon, Color.GRAY);
                }
                app.sectionName = title.isEmpty() ? "#" : title.substring(0, 1)
                        .toUpperCase(java.util.Locale.getDefault());
                allApps.data.add(app);
            } catch (Exception error) {
                Log.w(TAG, "Skipping invalid saved shortcut", error);
            }
        }
    }

    /** Dispatch pinned shortcuts via Android's launcher API, not via the browser URL. */
    public static boolean launchPinned(Context context, Intent intent) {
        if (!ACTION_PINNED.equals(intent.getAction())) return false;
        String pkg = intent.getStringExtra(EXTRA_PACKAGE);
        String id = intent.getStringExtra(EXTRA_ID);
        if (pkg == null || id == null) return true;
        try {
            context.getSystemService(LauncherApps.class).startShortcut(
                    pkg, id, null, null, Process.myUserHandle());
        } catch (Exception error) {
            Log.w(TAG, "Pinned shortcut no longer available", error);
            android.widget.Toast.makeText(context,
                    "Raccourci indisponible : réajoutez-le depuis l'accueil",
                    android.widget.Toast.LENGTH_LONG).show();
        }
        return true;
    }
}
