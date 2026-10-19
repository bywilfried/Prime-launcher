package com.android.launcher3;

import android.appwidget.AppWidgetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import java.io.ByteArrayOutputStream;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.util.Partner;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.widget.LauncherWidgetHolder;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Implements the layout parser with rules for internal layouts and partner layouts.
 */
public class DefaultLayoutParser extends AutoInstallsLayout {
    private static final String TAG = "DefaultLayoutParser";

    protected static final String TAG_RESOLVE = "resolve";
    private static final String TAG_FAVORITES = "favorites";
    protected static final String TAG_FAVORITE = "favorite";
    private static final String TAG_APPWIDGET = "appwidget";
    private static final String TAG_PRIME_ACTION = "prime-action";
    protected static final String TAG_SHORTCUT = "shortcut";
    private static final String TAG_FOLDER = "folder";
    private static final String TAG_PARTNER_FOLDER = "partner-folder";

    protected static final String ATTR_URI = "uri";
    private static final String ATTR_CONTAINER = "container";
    private static final String ATTR_SCREEN = "screen";
    private static final String ATTR_FOLDER_ITEMS = "folderItems";

    public static final String RES_PARTNER_FOLDER = "partner_folder";
    public static final String RES_PARTNER_DEFAULT_LAYOUT = "partner_default_layout";

    // TODO: Remove support for this broadcast, instead use widget options to send bind time options
    private static final String ACTION_APPWIDGET_DEFAULT_WORKSPACE_CONFIGURE =
            "com.android.launcher.action.APPWIDGET_DEFAULT_WORKSPACE_CONFIGURE";

    public DefaultLayoutParser(Context context, LauncherWidgetHolder appWidgetHolder,
            LayoutParserCallback callback, Resources sourceRes, int layoutId) {
        super(context, appWidgetHolder, callback, sourceRes, layoutId, TAG_FAVORITES);
    }

    @Override
    protected ArrayMap<String, TagParser> getFolderElementsMap() {
        ArrayMap<String, TagParser> parsers = new ArrayMap<>();
        parsers.put(TAG_FAVORITE, new AppShortcutWithUriParser());
        parsers.put(TAG_SHORTCUT, new ShortcutParser());
        return parsers;
    }

    @Override
    protected ArrayMap<String, TagParser> getLayoutElementsMap() {
        ArrayMap<String, TagParser> parsers = new ArrayMap<>();
        parsers.put(TAG_FAVORITE, new AppShortcutWithUriParser());
        parsers.put(TAG_APPWIDGET, new AppWidgetParser());
        parsers.put(TAG_SEARCH_WIDGET, new SearchWidgetParser());
        parsers.put(TAG_SHORTCUT, new ShortcutParser());
        parsers.put(TAG_RESOLVE, new ResolveParser());
        parsers.put(TAG_PRIME_ACTION, new PrimeActionParser());
        parsers.put("prime-default-app", new PrimeDefaultAppParser());
        parsers.put(TAG_FOLDER, new MyFolderParser());
        parsers.put(TAG_PARTNER_FOLDER, new PartnerFolderParser());
        return parsers;
    }

    @Override
    protected void parseContainerAndScreen(XmlPullParser parser, int[] out) {
        out[0] = LauncherSettings.Favorites.CONTAINER_DESKTOP;
        String strContainer = getAttributeValue(parser, ATTR_CONTAINER);
        if (strContainer != null) {
            out[0] = Integer.parseInt(strContainer);
        }
        out[1] = Integer.parseInt(getAttributeValue(parser, ATTR_SCREEN));
    }

    /**
     * AppShortcutParser which also supports adding URI based intents
     */
    public class AppShortcutWithUriParser extends AppShortcutParser {

        @Override
        protected int invalidPackageOrClass(XmlPullParser parser) {
            final String uri = getAttributeValue(parser, ATTR_URI);
            if (TextUtils.isEmpty(uri)) {
                Log.e(TAG, "Skipping invalid <favorite> with no component or uri");
                return -1;
            }

            final Intent metaIntent;
            try {
                metaIntent = Intent.parseUri(uri, 0);
            } catch (URISyntaxException e) {
                Log.e(TAG, "Unable to add meta-favorite: " + uri, e);
                return -1;
            }

            ResolveInfo resolved = mPackageManager.resolveActivity(metaIntent,
                    PackageManager.MATCH_DEFAULT_ONLY);
            final List<ResolveInfo> appList = mPackageManager.queryIntentActivities(
                    metaIntent, PackageManager.MATCH_DEFAULT_ONLY);

            // Verify that the result is an app and not just the resolver dialog asking which
            // app to use.
            if (resolved == null || resolved.activityInfo == null) {
                Log.w(TAG, "No matching activity for default favorite: " + metaIntent);
                return -1;
            }
            if (wouldLaunchResolverActivity(resolved, appList)) {
                // If only one of the results is a system app then choose that as the default.
                final ResolveInfo systemApp = getSingleSystemActivity(appList);
                if (systemApp == null) {
                    // There is no logical choice for this meta-favorite, so rather than making
                    // a bad choice just add nothing.
                    Log.w(TAG, "No preference or single system activity found for "
                            + metaIntent.toString());
                    return -1;
                }
                resolved = systemApp;
            }
            if (resolved == null || resolved.activityInfo == null) {
                Log.w(TAG, "No installed application resolves " + metaIntent);
                return -1;
            }
            final ActivityInfo info = resolved.activityInfo;
            final Intent intent = mPackageManager.getLaunchIntentForPackage(info.packageName);
            if (intent == null) {
                return -1;
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            return addShortcut(info.loadLabel(mPackageManager).toString(), intent,
                    Favorites.ITEM_TYPE_APPLICATION);
        }

        private ResolveInfo getSingleSystemActivity(List<ResolveInfo> appList) {
            ResolveInfo systemResolve = null;
            final int N = appList.size();
            for (int i = 0; i < N; ++i) {
                try {
                    ApplicationInfo info = mPackageManager.getApplicationInfo(
                            appList.get(i).activityInfo.packageName, 0);
                    if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                        if (systemResolve != null) {
                            return null;
                        } else {
                            systemResolve = appList.get(i);
                        }
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "Unable to get info about resolve results", e);
                    return null;
                }
            }
            return systemResolve;
        }

        private boolean wouldLaunchResolverActivity(ResolveInfo resolved,
                List<ResolveInfo> appList) {
            // If the list contains the above resolved activity, then it can't be
            // ResolverActivity itself.
            for (int i = 0; i < appList.size(); ++i) {
                ResolveInfo tmp = appList.get(i);
                if (tmp.activityInfo.name.equals(resolved.activityInfo.name)
                        && tmp.activityInfo.packageName.equals(resolved.activityInfo.packageName)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Contains a list of <favorite> nodes, and accepts the first successfully parsed node.
     */
    public class ResolveParser implements TagParser {

        private final AppShortcutWithUriParser mChildParser = new AppShortcutWithUriParser();

        @Override
        public int parseAndAdd(XmlPullParser parser) throws XmlPullParserException,
                IOException {
            final int groupDepth = parser.getDepth();
            int type;
            int addedId = -1;
            while ((type = parser.next()) != XmlPullParser.END_TAG ||
                    parser.getDepth() > groupDepth) {
                if (type != XmlPullParser.START_TAG || addedId > -1) {
                    continue;
                }
                final String fallback_item_name = parser.getName();
                if (TAG_FAVORITE.equals(fallback_item_name)) {
                    addedId = mChildParser.parseAndAdd(parser);
                } else {
                    Log.e(TAG, "Fallback groups can contain only favorites, found "
                            + fallback_item_name);
                }
            }
            return addedId;
        }
    }

    /**
     * A parser which adds a folder whose contents come from partner apk.
     */
    @Thunk
    class PartnerFolderParser implements TagParser {

        @Override
        public int parseAndAdd(XmlPullParser parser) throws XmlPullParserException,
                IOException {
            // Folder contents come from an external XML resource
            final Partner partner = Partner.get(mPackageManager);
            if (partner != null) {
                final int resId = partner.getXmlResId(RES_PARTNER_FOLDER);
                if (resId != 0) {
                    final XmlPullParser partnerParser = partner.getResources().getXml(resId);
                    beginDocument(partnerParser, TAG_FOLDER);
                    FolderParser folderParser = new FolderParser(getFolderElementsMap());
                    return folderParser.parseAndAdd(partnerParser);
                }
            }
            return -1;
        }
    }

    /**
     * An extension of FolderParser which allows adding items from a different xml.
     */
    @Thunk
    class MyFolderParser extends FolderParser {

        @Override
        public int parseAndAdd(XmlPullParser parser) throws XmlPullParserException,
                IOException {
            final int resId = getAttributeResourceValue(parser, ATTR_FOLDER_ITEMS, 0);
            if (resId != 0) {
                parser = mSourceRes.getXml(resId);
                beginDocument(parser, TAG_FOLDER);
            }
            return super.parseAndAdd(parser);
        }
    }


    /** Resolves the installed default app for a dock role, without requiring
     * the user to choose a resolver or relying on OEM package names. */
    protected class PrimeDefaultAppParser implements TagParser {
        @Override
        public int parseAndAdd(XmlPullParser parser) {
            String role = getAttributeValue(parser, "role");
            Intent intent;
            if ("contacts".equals(role)) {
                intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS);
            } else if ("messages".equals(role)) {
                intent = new Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:"));
            } else if ("phone".equals(role)) {
                intent = new Intent(Intent.ACTION_DIAL);
            } else {
                return -1;
            }
            try {
                String packageName = null;
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    android.app.role.RoleManager roles =
                            mContext.getSystemService(android.app.role.RoleManager.class);
                    String androidRole = "messages".equals(role)
                            ? android.app.role.RoleManager.ROLE_SMS
                            : "phone".equals(role) ? android.app.role.RoleManager.ROLE_DIALER : null;
                    if (roles != null && androidRole != null) {
                        List<String> holders = roles.getRoleHolders(androidRole);
                        if (!holders.isEmpty()) packageName = holders.get(0);
                    }
                }
                if (packageName == null) {
                    ResolveInfo resolved = mPackageManager.resolveActivity(intent,
                            PackageManager.MATCH_DEFAULT_ONLY);
                    if (resolved != null && resolved.activityInfo != null
                            && !resolved.activityInfo.packageName.equals("android")) {
                        packageName = resolved.activityInfo.packageName;
                    }
                }
                if (packageName == null && "contacts".equals(role)) {
                    // Some OEM contacts apps do not advertise CATEGORY_APP_CONTACTS.
                    Intent view = new Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("content://com.android.contacts/contacts"));
                    ResolveInfo resolved = mPackageManager.resolveActivity(view,
                            PackageManager.MATCH_DEFAULT_ONLY);
                    if (resolved != null && resolved.activityInfo != null) {
                        packageName = resolved.activityInfo.packageName;
                    }
                }
                if (packageName == null && "contacts".equals(role)) {
                    // Prefer the user's resolved Contacts handler, not a vendor package.
                    Intent contacts = new Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("content://com.android.contacts/contacts"));
                    ResolveInfo preferred = mPackageManager.resolveActivity(contacts,
                            PackageManager.MATCH_DEFAULT_ONLY);
                    if (preferred != null && preferred.activityInfo != null
                            && !"android".equals(preferred.activityInfo.packageName)
                            && !preferred.activityInfo.packageName.equals(mContext.getPackageName())) {
                        packageName = preferred.activityInfo.packageName;
                    }
                }
                if (packageName == null) {
                    Log.w(TAG, "No default app found for dock role: " + role);
                    return -1;
                }
                Intent launch = mPackageManager.getLaunchIntentForPackage(packageName);
                if (launch == null) {
                    // Dialer, SMS and contacts apps can expose only a role-specific entry point.
                    Intent roleIntent = new Intent(intent).setPackage(packageName);
                    ResolveInfo roleActivity = mPackageManager.resolveActivity(roleIntent, 0);
                    if (roleActivity != null && roleActivity.activityInfo != null) {
                        launch = new Intent(intent).setComponent(new ComponentName(
                                roleActivity.activityInfo.packageName,
                                roleActivity.activityInfo.name));
                    }
                }
                if (launch == null && "contacts".equals(role)) {
                    Intent contactsIntent = new Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("content://com.android.contacts/contacts"))
                            .setPackage(packageName);
                    ResolveInfo contactsActivity = mPackageManager.resolveActivity(contactsIntent, 0);
                    if (contactsActivity != null && contactsActivity.activityInfo != null) {
                        launch = contactsIntent.setComponent(new ComponentName(
                                contactsActivity.activityInfo.packageName,
                                contactsActivity.activityInfo.name));
                    }
                }
                if (launch == null) {
                    Log.w(TAG, "Default dock app has no launchable entry point: " + packageName);
                    return -1;
                }
                ResolveInfo launchInfo = mPackageManager.resolveActivity(launch, 0);
                String label = launchInfo != null ? launchInfo.loadLabel(mPackageManager).toString()
                        : role;
                return addShortcut(label, launch, Favorites.ITEM_TYPE_APPLICATION);
            } catch (RuntimeException e) {
                Log.e(TAG, "Unable to add default dock app: " + role, e);
                return -1;
            }
        }
    }

    /** Creates a built-in action shortcut without relying on a hard-coded application ID. */
    protected class PrimeActionParser implements TagParser {
        @Override
        public int parseAndAdd(XmlPullParser parser) {
            String action = getAttributeValue(parser, "action");
            if (!"drawer".equals(action) && !"search".equals(action)) {
                return -1;
            }
            String handler = "drawer".equals(action)
                    ? "{\"type\":\"openAppDrawer\"}"
                    : "{\"type\":\"openPrimeSearch\"}";
            Intent intent = new Intent("app.lawnchair.START_ACTION")
                    .setClassName(mContext, "app.lawnchair.gestures.ui.RunHandlerActivity")
                    .putExtra("app.lawnchair.EXTRA_HANDLER", handler);
            // Persist the actual action icon in the shortcut row. A plain shortcut
            // otherwise falls back to the Android generic launcher icon.
            try {
                app.lawnchair.gestures.config.GestureHandlerConfig config =
                        "drawer".equals(action)
                                ? app.lawnchair.gestures.config.GestureHandlerConfig.OpenAppDrawer.INSTANCE
                                : app.lawnchair.gestures.config.GestureHandlerConfig.OpenPrimeSearch.INSTANCE;
                Drawable drawable = config.getIcon(mContext).loadDrawable(mContext);
                if (drawable != null) {
                    int size = (int) (48 * mContext.getResources().getDisplayMetrics().density);
                    size = Math.max(1, size);
                    Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    // Use Android's adaptive icon mask instead of painting an
                    // approximate rounded rectangle into the shortcut bitmap.
                    // Manually added Actions shortcuts use ShortcutInfo icons,
                    // which are normalized and masked by the launcher.
                    android.graphics.drawable.Drawable foreground =
                            new android.graphics.drawable.InsetDrawable(drawable, 0.20f);
                    android.graphics.drawable.AdaptiveIconDrawable adaptive =
                            new android.graphics.drawable.AdaptiveIconDrawable(
                                    new android.graphics.drawable.ColorDrawable(
                                            android.graphics.Color.WHITE),
                                    foreground);
                    adaptive.setBounds(0, 0, size, size);
                    adaptive.draw(canvas);
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                    mValues.put(Favorites.ICON, output.toByteArray());
                    bitmap.recycle();
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to render Prime action shortcut icon", e);
            }
            return addShortcut("drawer".equals(action) ? "Tiroir" : "Prime Search",
                    intent, Favorites.ITEM_TYPE_SHORTCUT);
        }
    }

    /**
     * AppWidget parser which enforces that the app is already installed when the layout is parsed.
     */
    protected class AppWidgetParser extends PendingWidgetParser {

        @Override
        public ComponentName getComponentName(XmlPullParser parser) {
            ComponentName component = super.getComponentName(parser);
            if (component != null && "self".equals(component.getPackageName())) {
                return new ComponentName(mContext.getPackageName(), component.getClassName());
            }
            return component;
        }

        @Override
        protected int verifyAndInsert(ComponentName cn, Bundle extras) {
            try {
                mPackageManager.getReceiverInfo(cn, 0);
            } catch (Exception e) {
                String[] packages = mPackageManager.currentToCanonicalPackageNames(
                        new String[]{cn.getPackageName()});
                cn = new ComponentName(packages[0], cn.getClassName());
                try {
                    mPackageManager.getReceiverInfo(cn, 0);
                } catch (Exception e1) {
                    Log.w(TAG, "Widget provider not ready; preserving placeholder: " + cn);
                    return super.verifyAndInsert(cn, extras);
                }
            }

            final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(mContext);
            int insertedId = -1;
            try {
                int appWidgetId = mAppWidgetHolder.allocateAppWidgetId();

                if (!appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, cn)) {
                    Log.e(TAG, "Unable to bind app widget id " + cn + "; keeping pending widget");
                    mAppWidgetHolder.deleteAppWidgetId(appWidgetId);
                    return super.verifyAndInsert(cn, extras);
                }

                mValues.put(Favorites.APPWIDGET_ID, appWidgetId);
                mValues.put(Favorites.APPWIDGET_PROVIDER, cn.flattenToString());
                mValues.put(Favorites._ID, mCallback.generateNewItemId());
                insertedId = mCallback.insertAndCheck(mDb, mValues);
                if (insertedId < 0) {
                    mAppWidgetHolder.deleteAppWidgetId(appWidgetId);
                    return insertedId;
                }

                // Send a broadcast to configure the widget
                if (!extras.isEmpty()) {
                    Intent intent = new Intent(ACTION_APPWIDGET_DEFAULT_WORKSPACE_CONFIGURE);
                    intent.setComponent(cn);
                    intent.putExtras(extras);
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                    mContext.sendBroadcast(intent);
                }
            } catch (RuntimeException ex) {
                Log.e(TAG, "Problem allocating appWidgetId", ex);
            }
            return insertedId;
        }
    }
}
