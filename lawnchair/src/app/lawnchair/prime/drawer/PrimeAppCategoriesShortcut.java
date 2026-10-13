package app.lawnchair.prime.drawer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.util.TypedValue;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.views.ActivityContext;

import java.util.HashSet;
import java.util.Set;

import app.lawnchair.preferences.PreferenceManager;

/** Prime category assignment exposed from the native app long-press popup. */
public class PrimeAppCategoriesShortcut extends SystemShortcut<ActivityContext> {

    public static final Factory<ActivityContext> FACTORY = (context, itemInfo, originalView) -> {
        Context androidContext = context.asContext();
        if (!PreferenceManager.getInstance(androidContext).getDrawerTabsEnabled().get()
                || itemInfo.itemType != LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
            return null;
        }
        PrimeDrawerTabsConfiguration configuration =
                new PrimeDrawerTabsRepository(androidContext).getConfiguration();
        boolean hasUserTab = configuration.getTabs().stream().anyMatch(tab -> !tab.isSystem());
        return hasUserTab
                ? new PrimeAppCategoriesShortcut(context, itemInfo, originalView)
                : null;
    };

    public PrimeAppCategoriesShortcut(
            ActivityContext target, ItemInfo itemInfo, @NonNull View originalView) {
        super(R.drawable.prime_ic_categories, R.string.prime_app_categories,
                target, itemInfo, originalView, false);
    }

    @Override
    public void onClick(View view) {
        if (mItemInfo.getTargetComponent() == null) return;
        dismissTaskMenuView();

        Context context = view.getContext();
        PrimeDrawerTabsRepository repository = new PrimeDrawerTabsRepository(context);
        PrimeDrawerTabsConfiguration configuration = repository.getConfiguration();
        ComponentKey appKey = new ComponentKey(mItemInfo.getTargetComponent(), mItemInfo.user);

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 16);
        list.setPadding(padding, dp(context, 8), padding, dp(context, 8));

        for (PrimeDrawerTab tab : configuration.getTabs()) {
            if (tab.isSystem()) continue;
            CheckBox checkBox = new CheckBox(context);
            checkBox.setText(tab.getTitle());
            TypedValue textColor = new TypedValue();
            if (context.getTheme().resolveAttribute(
                    android.R.attr.textColorPrimary, textColor, true)) {
                if (textColor.resourceId != 0) {
                    checkBox.setTextColor(context.getColorStateList(textColor.resourceId));
                } else {
                    checkBox.setTextColor(textColor.data);
                }
            }
            checkBox.setTag(tab.getId());
            checkBox.setChecked(tab.getApps().contains(appKey.toString()));
            checkBox.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4));
            list.addView(checkBox, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        ScrollView scroll = new ScrollView(context);
        scroll.addView(list);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.prime_app_categories)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(button -> {
                    Set<String> selectedTabIds = new HashSet<>();
                    for (int i = 0; i < list.getChildCount(); i++) {
                        View child = list.getChildAt(i);
                        if (child instanceof CheckBox && ((CheckBox) child).isChecked()) {
                            selectedTabIds.add((String) child.getTag());
                        }
                    }
                    repository.setAppTabs(appKey, selectedTabIds);
                    dialog.dismiss();
                    if (mTarget.getAppsView() != null
                            && mTarget.getAppsView().getFloatingHeaderView() != null) {
                        mTarget.getAppsView().getFloatingHeaderView().onPrimeDrawerTabSelected();
                    }
                }));
        dialog.show();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
