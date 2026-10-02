package app.lawnchair.prime.drawer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.content.DialogInterface;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.util.TypedValue;

import androidx.annotation.Nullable;
import android.app.AlertDialog;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;

import com.android.launcher3.R;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.allapps.FloatingHeaderRow;
import com.android.launcher3.allapps.FloatingHeaderView;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.OptionsPopupView;
import com.android.launcher3.logging.StatsLogManager.LauncherEvent;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import app.lawnchair.preferences.PreferenceManager;
import app.lawnchair.preferences2.PreferenceManager2;
import app.lawnchair.ui.preferences.PreferenceActivity;
import app.lawnchair.ui.preferences.navigation.PrimeDrawerCategory;
import app.lawnchair.ui.preferences.navigation.PrimeDrawerCategoryFolders;
import app.lawnchair.ui.preferences.navigation.PrimeDrawerCategoryAdvanced;
import app.lawnchair.ui.preferences.navigation.PrimeDrawerCategories;
import app.lawnchair.ui.preferences.navigation.AppDrawer;

/** Prime's independent horizontal drawer tab row. */
public class PrimeDrawerTabsView extends HorizontalScrollView implements FloatingHeaderRow, SharedPreferences.OnSharedPreferenceChangeListener {

    private final PreferenceManager mPrefs;
    private final PrimeDrawerTabsRepository mRepository;
    private final LinearLayout mTabsContainer;
    private boolean mIsScrolledOut;
    private OptionsPopupView<?> mTabPopup;
    private String mDraggingTabId;
    private float mLongPressDownX;
    private float mLastTouchRawX;
    private float mLastTouchRawY;
    private View mGesturePill;
    private String mGestureTabId;
    private boolean mLongPressActive;
    private final int mTouchSlop;
    private FloatingHeaderView mHeaderParent;
    private boolean mSwipeCommitInProgress;
    private String mSwipePreviewTabId;
    private int mSwipeTabsStartScrollX;
    private int mSwipeTabsTargetScrollX;
    private float mRowDownX;
    private float mRowDownY;
    private boolean mRowHorizontalScroll;

    public PrimeDrawerTabsView(Context context) {
        this(context, null);
    }

    public PrimeDrawerTabsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mPrefs = PreferenceManager.getInstance(context);
        mRepository = new PrimeDrawerTabsRepository(context);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mTabsContainer = new LinearLayout(context);
        mTabsContainer.setOrientation(LinearLayout.HORIZONTAL);
        mTabsContainer.setGravity(Gravity.CENTER_VERTICAL);
        addView(mTabsContainer, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        setHorizontalScrollBarEnabled(false);
        setOnTouchListener((v, event) -> handleRowTouch(event));
    }

    @Override
    public void setup(FloatingHeaderView parent, FloatingHeaderRow[] rows, boolean tabsHidden) {
        mHeaderParent = parent;
        refresh(parent);
        if (parent.getParent() instanceof ActivityAllAppsContainerView) {
            ((ActivityAllAppsContainerView<?>) parent.getParent())
                    .setPrimeDrawerSwipeListener(
                            () -> mPrefs.getDrawerTabsEnabled().get()
                                    && mPrefs.getDrawerTabsSwipeEnabled().get(),
                            swipeLeft -> {
                                String targetTabId = getSwipeTargetTabId(swipeLeft);
                                setSwipePreviewTab(targetTabId);
                                return targetTabId;
                            },
                            progress -> updateSwipeTabsScroll(progress),
                            swipeLeft -> commitSwipeTab(parent, swipeLeft),
                            committed -> clearSwipePreview(committed));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mRepository.registerConfigurationChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        mRepository.unregisterConfigurationChangeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if ((!PrimeDrawerTabsRepository.PREF_CONFIGURATION.equals(key)
                && !PrimeDrawerTabsRepository.PREF_SELECTED_TAB.equals(key))
                || mHeaderParent == null) return;
        post(() -> {
            if (mSwipeCommitInProgress
                    && PrimeDrawerTabsRepository.PREF_SELECTED_TAB.equals(key)) {
                // The persistent destination page is already live. A swipe selection change must
                // update only the pill state; rebuilding app content here recreates the handoff.
                mSwipeCommitInProgress = false;
                updateSwipePillSelection(mRepository.getConfiguration().getSelectedTabId());
                return;
            }
            if (PrimeDrawerTabsRepository.PREF_SELECTED_TAB.equals(key)) {
                // Direct tab clicks refresh synchronously in selectTab(). Do not run a second
                // asynchronous refresh for the same selection change.
                return;
            }
            refresh(mHeaderParent);
            mHeaderParent.onPrimeDrawerTabSelected();
        });
    }

    private void refresh(FloatingHeaderView parent) {
        refreshForConfiguration(parent, mRepository.getConfiguration());
    }

    private void refreshForConfiguration(
            FloatingHeaderView parent, PrimeDrawerTabsConfiguration configuration) {
        boolean enabled = mPrefs.getDrawerTabsEnabled().get();
        setVisibility(enabled && !mIsScrolledOut ? VISIBLE : enabled ? INVISIBLE : GONE);
        mTabsContainer.removeAllViews();
        if (!enabled) return;
        updateStickyBackground(configuration);
        List<PrimeDrawerTab> tabs = configuration.getTabs();
        boolean hasUserTabs = false;
        for (PrimeDrawerTab candidate : tabs) {
            if (!candidate.isSystem()) {
                hasUserTabs = true;
                break;
            }
        }
        for (PrimeDrawerTab tab : tabs) {
            if (!isTabVisible(tab, hasUserTabs)) continue;
            String label;
            if (PrimeDrawerTabsRepository.ALL_TAB_ID.equals(tab.getId())) {
                label = getContext().getString(R.string.prime_tab_all);
            } else if (PrimeDrawerTabsRepository.UNCLASSIFIED_TAB_ID.equals(tab.getId())) {
                label = getContext().getString(R.string.prime_tab_unclassified);
            } else {
                label = tab.getTitle();
            }
            final String tabId = tab.getId();
            Integer tabColor = tab.getVisualOverrides().getTabColor();
            TextView pill = addPill(label, tabId.equals(configuration.getSelectedTabId()), tabColor, () ->
                    selectTab(parent, tabId, getDirectTabDirection(tabId)));
            pill.setTag(tabId);
            pill.setOnTouchListener((v, event) -> {
                mLastTouchRawX = event.getRawX();
                mLastTouchRawY = event.getRawY();
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    mLongPressDownX = event.getRawX();
                    mLongPressActive = false;
                } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                        && mLongPressActive
                        && Math.abs(event.getRawX() - mLongPressDownX) > mTouchSlop) {
                    if (mTabPopup != null) {
                        mTabPopup.close(false);
                        mTabPopup = null;
                    }
                    mLongPressActive = false;
                    mDraggingTabId = tabId;
                    mGesturePill = v;
                    mGestureTabId = tabId;
                    reorderDraggedTab(event.getRawX());
                    return true;
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    mLongPressActive = false;
                }
                return false;
            });
            pill.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                mLongPressActive = true;
                mLongPressDownX = mLastTouchRawX;
                mGesturePill = v;
                mGestureTabId = tabId;
                showTabMenu(parent, tab, pill);
                return true;
            });
        }
        addPill("+", false, null, () -> showCreateTabDialog(parent));
        ensureSelectedTabVisible(configuration.getSelectedTabId());
    }

    private void updateStickyBackground(PrimeDrawerTabsConfiguration configuration) {
        // The tab row must not paint another drawer background layer. Keeping it transparent
        // lets the active drawer background show through without darkening or covering items.
        setBackgroundColor(android.graphics.Color.TRANSPARENT);
    }

    private void setSwipePreviewTab(String tabId) {
        if (tabId == null || tabId.equals(mSwipePreviewTabId)) return;
        mSwipePreviewTabId = tabId;
        updateSwipePillSelection(tabId);
        mSwipeTabsStartScrollX = getScrollX();
        mSwipeTabsTargetScrollX = getCenteredScrollX(tabId);
    }

    private int getCenteredScrollX(String tabId) {
        View selected = mTabsContainer.findViewWithTag(tabId);
        if (selected == null || getWidth() == 0) return getScrollX();

        int maxScroll = Math.max(0,
                mTabsContainer.getWidth() + getPaddingLeft() + getPaddingRight() - getWidth());

        // For the rightmost category, reveal the end of the row instead of centering the pill.
        // The trailing "+" action must remain visible next to the selected last category.
        boolean isLastTab = true;
        for (int i = mTabsContainer.indexOfChild(selected) + 1;
                i < mTabsContainer.getChildCount(); i++) {
            if (mTabsContainer.getChildAt(i).getTag() instanceof String) {
                isLastTab = false;
                break;
            }
        }
        if (isLastTab) return maxScroll;

        int viewportWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int selectedCenter = selected.getLeft() + selected.getWidth() / 2;
        int desiredScroll = selectedCenter - viewportWidth / 2 - getPaddingLeft();
        return Math.max(0, Math.min(desiredScroll, maxScroll));
    }

    private void updateSwipeTabsScroll(float progress) {
        if (mSwipePreviewTabId == null) return;
        int scrollX = Math.round(mSwipeTabsStartScrollX
                + (mSwipeTabsTargetScrollX - mSwipeTabsStartScrollX) * progress);
        scrollTo(scrollX, 0);
    }

    private void clearSwipePreview(boolean committed) {
        String selectedTabId = mRepository.getConfiguration().getSelectedTabId();
        mSwipePreviewTabId = null;
        updateSwipePillSelection(selectedTabId);
        if (committed) ensureSelectedTabVisible(selectedTabId);
    }

    private void updateSwipePillSelection(String tabId) {
        PrimeDrawerTabsConfiguration configuration = mRepository.getConfiguration();

        // Never rebuild the tab row for a transient swipe preview. Recreating every pill here can
        // detach the view that is currently receiving a normal tap, making ACTION_UP/click appear
        // unreliable after the swipe experiment was enabled.
        for (int i = 0; i < mTabsContainer.getChildCount(); i++) {
            View child = mTabsContainer.getChildAt(i);
            Object tag = child.getTag();
            if (!(child instanceof TextView) || !(tag instanceof String)) continue;

            String childTabId = (String) tag;
            PrimeDrawerTab childTab = null;
            for (PrimeDrawerTab candidate : configuration.getTabs()) {
                if (childTabId.equals(candidate.getId())) {
                    childTab = candidate;
                    break;
                }
            }
            if (childTab == null) continue;
            applyPillSelectionStyle(
                    (TextView) child,
                    childTabId.equals(tabId),
                    childTab.getVisualOverrides().getTabColor());
        }
    }

    private void applyPillSelectionStyle(
            TextView pill, boolean selected, @Nullable Integer selectedColor) {
        pill.setTextColor(Themes.getAttrColor(
                getContext(), selected ? android.R.attr.colorBackground : android.R.attr.textColorPrimary));

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(20));
        if (selected) {
            Integer modeTabColor = resolveModeTabColor();
            int defaultTabColor = resolveDefaultTabColor();
            background.setColor(selectedColor != null
                    ? selectedColor
                    : modeTabColor != null ? modeTabColor : defaultTabColor);
        } else {
            background.setColor(0x00000000);
            background.setStroke(dp(1),
                    Themes.getAttrColor(getContext(), android.R.attr.textColorSecondary));
        }
        pill.setBackground(background);
    }

    private void commitSwipeTab(FloatingHeaderView parent, boolean swipeLeft) {
        String targetTabId = getSwipeTargetTabId(swipeLeft);
        if (targetTabId == null) return;
        PrimeDrawerTabsConfiguration configuration = mRepository.getConfiguration();
        if (targetTabId.equals(configuration.getSelectedTabId())) return;

        // The arrived RecyclerView is promoted to the active Prime page by the container.
        // Persist only the selection here: rebuilding the old live RecyclerView would recreate
        // the preview->live handoff that persistent pages are specifically meant to remove.
        mSwipeCommitInProgress = true;
        mRepository.setSelectedTab(targetTabId);
    }

    private int getDirectTabDirection(String targetTabId) {
        PrimeDrawerTabsConfiguration configuration = mRepository.getConfiguration();
        boolean hasUserTabs = false;
        for (PrimeDrawerTab tab : configuration.getTabs()) {
            if (!tab.isSystem()) {
                hasUserTabs = true;
                break;
            }
        }

        int currentIndex = -1;
        int targetIndex = -1;
        int visibleIndex = 0;
        for (PrimeDrawerTab tab : configuration.getTabs()) {
            if (!isTabVisible(tab, hasUserTabs)) continue;
            if (tab.getId().equals(configuration.getSelectedTabId())) {
                currentIndex = visibleIndex;
            }
            if (tab.getId().equals(targetTabId)) {
                targetIndex = visibleIndex;
            }
            visibleIndex++;
        }
        if (currentIndex < 0 || targetIndex < 0 || currentIndex == targetIndex) return 0;
        return targetIndex > currentIndex ? 1 : -1;
    }

    private void selectTab(FloatingHeaderView parent, String tabId, int direction) {
        PrimeDrawerTabsConfiguration configuration = mRepository.getConfiguration();
        if (tabId.equals(configuration.getSelectedTabId())) {
            ensureSelectedTabVisible(tabId);
            return;
        }
        if (parent.getParent() instanceof ActivityAllAppsContainerView) {
            ActivityAllAppsContainerView<?> container =
                    (ActivityAllAppsContainerView<?>) parent.getParent();
            // direction > 0 means the destination is to the right in tab order, so pages move
            // left. The destination may be several tabs away; it is still prepared as one direct
            // neighbor and intermediate categories are never animated.
            boolean moveLeft = direction > 0;
            setSwipePreviewTab(tabId);
            boolean started = container.animatePrimeTabSelection(tabId, moveLeft, () -> {
                // The prepared destination page is already the final page, exactly like a swipe
                // commit. Persist selection only; refreshing here would rebuild the outgoing page
                // during the handoff and undo the persistent-page guarantee.
                mSwipeCommitInProgress = true;
                mRepository.setSelectedTab(tabId);
            });
            if (started) return;
        }

        // Fallback for a not-yet-laid-out drawer: preserve the validated synchronous behavior.
        mRepository.setSelectedTab(tabId);
        refresh(parent);
        parent.onPrimeDrawerTabSelected(direction);
    }

    private void ensureSelectedTabVisible(String tabId) {
        post(() -> {
            View selected = mTabsContainer.findViewWithTag(tabId);
            if (selected == null || getWidth() == 0) return;

            int desiredScroll = getCenteredScrollX(tabId);
            if (desiredScroll != getScrollX()) smoothScrollTo(desiredScroll, 0);
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        mLastTouchRawX = event.getRawX();
        mLastTouchRawY = event.getRawY();
        if (mLongPressActive && mGesturePill != null && mGestureTabId != null
                && event.getActionMasked() == MotionEvent.ACTION_MOVE
                && Math.abs(event.getRawX() - mLongPressDownX) > mTouchSlop) {
            if (mTabPopup != null) {
                mTabPopup.close(false);
                mTabPopup = null;
            }
            mLongPressActive = false;
            mDraggingTabId = mGestureTabId;
            getParent().requestDisallowInterceptTouchEvent(true);
            reorderDraggedTab(event.getRawX());
            return true;
        }
        if (mDraggingTabId != null) {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                reorderDraggedTab(event.getRawX());
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                persistCurrentOrder();
                mDraggingTabId = null;
                mGesturePill = null;
                mGestureTabId = null;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    public void onDrawerOpening() {
        if (!mPrefs.getDrawerTabsEnabled().get()) return;
        PrimeDrawerTabsConfiguration configuration = mRepository.getConfiguration();
        String openMode = mPrefs.getDrawerTabsOpenMode().get();
        String targetTabId = configuration.getSelectedTabId();
        boolean hasUserTabs = false;
        for (PrimeDrawerTab tab : configuration.getTabs()) {
            if (!tab.isSystem()) {
                hasUserTabs = true;
                break;
            }
        }
        List<PrimeDrawerTab> visibleTabs = new ArrayList<>();
        for (PrimeDrawerTab tab : configuration.getTabs()) {
            if (isTabVisible(tab, hasUserTabs)) visibleTabs.add(tab);
        }
        if (visibleTabs.isEmpty()) return;
        if ("first".equals(openMode)) {
            targetTabId = visibleTabs.get(0).getId();
        } else if ("default".equals(openMode)) {
            targetTabId = configuration.getDefaultTabId();
        }
        boolean targetVisible = false;
        for (PrimeDrawerTab tab : visibleTabs) {
            if (tab.getId().equals(targetTabId)) {
                targetVisible = true;
                break;
            }
        }
        if (!targetVisible) targetTabId = visibleTabs.get(0).getId();
        if (!targetTabId.equals(configuration.getSelectedTabId())) {
            mRepository.setSelectedTab(targetTabId);
        }
        if (getParent() instanceof FloatingHeaderView) {
            FloatingHeaderView parent = (FloatingHeaderView) getParent();
            refresh(parent);
            parent.onPrimeDrawerTabSelected();
        }
    }

    private String getSwipeTargetTabId(boolean swipeLeft) {
        if (!mPrefs.getDrawerTabsEnabled().get() || !mPrefs.getDrawerTabsSwipeEnabled().get()) {
            return null;
        }

        PrimeDrawerTabsConfiguration configuration = mRepository.getConfiguration();
        boolean hasUserTabs = false;
        for (PrimeDrawerTab tab : configuration.getTabs()) {
            if (!tab.isSystem()) {
                hasUserTabs = true;
                break;
            }
        }

        List<PrimeDrawerTab> visibleTabs = new ArrayList<>();
        for (PrimeDrawerTab tab : configuration.getTabs()) {
            if (isTabVisible(tab, hasUserTabs)) visibleTabs.add(tab);
        }
        if (visibleTabs.size() < 2) return null;

        int currentIndex = 0;
        for (int i = 0; i < visibleTabs.size(); i++) {
            if (visibleTabs.get(i).getId().equals(configuration.getSelectedTabId())) {
                currentIndex = i;
                break;
            }
        }

        int targetIndex = swipeLeft ? currentIndex + 1 : currentIndex - 1;
        if (targetIndex < 0 || targetIndex >= visibleTabs.size()) return null;
        return visibleTabs.get(targetIndex).getId();
    }

    private boolean switchTabBySwipe(FloatingHeaderView parent, boolean swipeLeft) {
        if (!mPrefs.getDrawerTabsEnabled().get() || !mPrefs.getDrawerTabsSwipeEnabled().get()) return false;

        PrimeDrawerTabsConfiguration configuration = mRepository.getConfiguration();
        boolean hasUserTabs = false;
        for (PrimeDrawerTab tab : configuration.getTabs()) {
            if (!tab.isSystem()) {
                hasUserTabs = true;
                break;
            }
        }

        List<PrimeDrawerTab> visibleTabs = new ArrayList<>();
        for (PrimeDrawerTab tab : configuration.getTabs()) {
            if (isTabVisible(tab, hasUserTabs)) visibleTabs.add(tab);
        }
        if (visibleTabs.size() < 2) return false;

        int currentIndex = 0;
        for (int i = 0; i < visibleTabs.size(); i++) {
            if (visibleTabs.get(i).getId().equals(configuration.getSelectedTabId())) {
                currentIndex = i;
                break;
            }
        }

        int targetIndex = swipeLeft ? currentIndex + 1 : currentIndex - 1;
        if (targetIndex < 0 || targetIndex >= visibleTabs.size()) return false;

        selectTab(parent, visibleTabs.get(targetIndex).getId(), swipeLeft ? 1 : -1);
        return true;
    }

    private boolean isTabVisible(PrimeDrawerTab tab, boolean hasUserTabs) {
        if (PrimeDrawerTabsRepository.ALL_TAB_ID.equals(tab.getId())) {
            return !mPrefs.getDrawerTabsHideAll().get()
                    || (!hasUserTabs && mPrefs.getDrawerTabsHideUnclassified().get());
        }
        if (PrimeDrawerTabsRepository.UNCLASSIFIED_TAB_ID.equals(tab.getId())) {
            return !mPrefs.getDrawerTabsHideUnclassified().get();
        }
        return true;
    }

    private boolean handleRowTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mRowDownX = event.getRawX();
                mRowDownY = event.getRawY();
                mRowHorizontalScroll = false;
                // This row owns horizontal navigation of the category strip. Claim the touch
                // sequence immediately so the surrounding drawer cannot reinterpret the same
                // drag as a page swipe before HorizontalScrollView crosses its own touch slop.
                getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - mRowDownX;
                float dy = event.getRawY() - mRowDownY;
                if (!mRowHorizontalScroll
                        && Math.abs(dx) > mTouchSlop
                        && Math.abs(dx) > Math.abs(dy)) {
                    mRowHorizontalScroll = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                mRowHorizontalScroll = false;
                return false;
            default:
                return false;
        }
    }

    private void reorderDraggedTab(float rawX) {
        View dragged = mGesturePill;
        if (dragged == null) return;

        int from = mTabsContainer.indexOfChild(dragged);
        if (from < 0) return;

        int[] containerLocation = new int[2];
        mTabsContainer.getLocationOnScreen(containerLocation);
        float x = rawX - containerLocation[0];

        int tabCount = 0;
        for (int i = 0; i < mTabsContainer.getChildCount(); i++) {
            if (mTabsContainer.getChildAt(i).getTag() instanceof String) tabCount++;
        }

        int to = from;
        if (from > 0) {
            View left = mTabsContainer.getChildAt(from - 1);
            if (left.getTag() instanceof String
                    && x < (left.getLeft() + left.getRight()) / 2f) {
                to = from - 1;
            }
        }
        if (to == from && from < tabCount - 1) {
            View right = mTabsContainer.getChildAt(from + 1);
            if (right.getTag() instanceof String
                    && x > (right.getLeft() + right.getRight()) / 2f) {
                to = from + 1;
            }
        }

        if (to == from) return;
        mTabsContainer.removeViewAt(from);
        mTabsContainer.addView(dragged, to);
    }

    private void persistCurrentOrder() {
        ArrayList<String> ids = new ArrayList<>();
        for (int i = 0; i < mTabsContainer.getChildCount(); i++) {
            Object tag = mTabsContainer.getChildAt(i).getTag();
            if (tag instanceof String) ids.add((String) tag);
        }
        mRepository.reorderTabs(ids);
    }

    private void showTabMenu(FloatingHeaderView parent, PrimeDrawerTab tab, View anchor) {
        ActivityContext activityContext = ActivityContext.lookupContext(getContext());
        if (activityContext == null) return;
        ArrayList<OptionsPopupView.OptionItem> items = new ArrayList<>();

        items.add(option("Réorganiser", v -> {
            getContext().startActivity(PreferenceActivity.createIntent(
                    getContext(), PrimeDrawerCategories.INSTANCE));
            return true;
        }));
        if (!tab.isSystem()) {
            items.add(option("Modifier la catégorie", v -> {
                getContext().startActivity(PreferenceActivity.createIntent(
                        getContext(), new PrimeDrawerCategory(tab.getId())));
                return true;
            }));
            items.add(option(R.string.app_drawer_folder, v -> {
                getContext().startActivity(PreferenceActivity.createIntent(
                        getContext(), new PrimeDrawerCategoryFolders(tab.getId())));
                return true;
            }));
        }
        items.add(option("Options du tiroir", v -> {
            getContext().startActivity(PreferenceActivity.createIntent(
                    getContext(), AppDrawer.INSTANCE));
            return true;
        }));
        items.add(option("Diagnostic swipe", v -> {
            showSwipeDiagnostic(parent);
            return true;
        }));
        if (!tab.getId().equals(mRepository.getConfiguration().getDefaultTabId())) {
            items.add(option(R.string.prime_tab_set_default, v -> {
                mRepository.setDefaultTab(tab.getId());
                return true;
            }));
        }

        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        RectF target = new RectF(location[0], location[1],
                location[0] + anchor.getWidth(), location[1] + anchor.getHeight());
        mTabPopup = OptionsPopupView.show(activityContext, target, items, false);
    }

    private void showSwipeDiagnostic(FloatingHeaderView parent) {
        if (!(parent.getParent() instanceof ActivityAllAppsContainerView)) return;
        ActivityAllAppsContainerView<?> allApps =
                (ActivityAllAppsContainerView<?>) parent.getParent();

        TextView logView = new TextView(getContext());
        logView.setText(allApps.getPrimeSwipeDebugLog());
        logView.setTextIsSelectable(true);
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        int padding = dp(16);
        logView.setPadding(padding, padding, padding, padding);

        ScrollView scroll = new ScrollView(getContext());
        scroll.addView(logView, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("Diagnostic swipe Prime")
                .setView(scroll)
                .setNegativeButton("Fermer", null)
                .setNeutralButton("Effacer", null)
                .setPositiveButton("Copier", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                ClipboardManager clipboard =
                        (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(
                        ClipData.newPlainText("Prime swipe diagnostic", allApps.getPrimeSwipeDebugLog()));
            });
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
                allApps.clearPrimeSwipeDebugLog();
                logView.setText(allApps.getPrimeSwipeDebugLog());
            });
        });
        dialog.show();
    }

    private void showAppsDialog(FloatingHeaderView parent, PrimeDrawerTab tab) {
        if (!(parent.getParent() instanceof ActivityAllAppsContainerView)) return;
        ActivityAllAppsContainerView<?> allApps = (ActivityAllAppsContainerView<?>) parent.getParent();
        AppInfo[] apps = allApps.getAppsStore().getApps();
        Arrays.sort(apps = apps.clone(), Comparator.comparing(
                app -> app.title == null ? "" : app.title.toString(),
                String.CASE_INSENSITIVE_ORDER));

        Set<String> selected = new HashSet<>(tab.getApps());
        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = dp(16);
        list.setPadding(horizontalPadding, dp(8), horizontalPadding, dp(8));

        for (AppInfo app : apps) {
            CheckBox checkBox = new CheckBox(getContext());
            String key = app.toComponentKey().toString();
            checkBox.setText(app.title);
            android.graphics.drawable.Drawable icon = app.newIcon(getContext());
            int iconSize = dp(32);
            icon.setBounds(0, 0, iconSize, iconSize);
            checkBox.setCompoundDrawablesRelative(icon, null, null, null);
            checkBox.setCompoundDrawablePadding(dp(12));
            TypedValue textColor = new TypedValue();
            if (getContext().getTheme().resolveAttribute(
                    android.R.attr.textColorPrimary, textColor, true)) {
                if (textColor.resourceId != 0) {
                    checkBox.setTextColor(getContext().getColorStateList(textColor.resourceId));
                } else {
                    checkBox.setTextColor(textColor.data);
                }
            }
            checkBox.setTag(key);
            checkBox.setChecked(selected.contains(key));
            checkBox.setPadding(dp(8), dp(4), dp(8), dp(4));
            list.addView(checkBox, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        ScrollView scroll = new ScrollView(getContext());
        scroll.addView(list);
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(tab.getTitle())
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                    Set<com.android.launcher3.util.ComponentKey> keys = new HashSet<>();
                    for (int i = 0; i < list.getChildCount(); i++) {
                        View child = list.getChildAt(i);
                        if (child instanceof CheckBox && ((CheckBox) child).isChecked()) {
                            com.android.launcher3.util.ComponentKey key =
                                    com.android.launcher3.util.ComponentKey.fromString((String) child.getTag());
                            if (key != null) keys.add(key);
                        }
                    }
                    mRepository.setTabApps(tab.getId(), keys);
                    dialog.dismiss();
                    parent.onPrimeDrawerTabSelected();
                }));
        dialog.show();
    }

    private OptionsPopupView.OptionItem option(String label, View.OnLongClickListener action) {
        return new OptionsPopupView.OptionItem(
                label,
                new ColorDrawable(android.graphics.Color.TRANSPARENT),
                LauncherEvent.IGNORE,
                action);
    }

    private OptionsPopupView.OptionItem optionUnimplemented(int labelRes) {
        return new OptionsPopupView.OptionItem(
                getContext().getString(labelRes) + "*",
                new ColorDrawable(android.graphics.Color.TRANSPARENT),
                LauncherEvent.IGNORE,
                v -> false);
    }

    private OptionsPopupView.OptionItem option(int labelRes, View.OnLongClickListener action) {
        return new OptionsPopupView.OptionItem(
                getContext().getString(labelRes),
                new ColorDrawable(android.graphics.Color.TRANSPARENT),
                LauncherEvent.IGNORE,
                action);
    }

    private String getSystemTabLabel(PrimeDrawerTab tab) {
        return PrimeDrawerTabsRepository.ALL_TAB_ID.equals(tab.getId())
                ? getContext().getString(R.string.prime_tab_all)
                : getContext().getString(R.string.prime_tab_unclassified);
    }

    private void showRenameTabDialog(FloatingHeaderView parent, PrimeDrawerTab tab) {
        EditText input = new EditText(getContext());
        input.setText(tab.getTitle());
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        int horizontalPadding = dp(24);
        LinearLayout container = new LinearLayout(getContext());
        container.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        container.addView(input, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.prime_tab_rename)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.prime_tab_rename_action, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                String title = input.getText().toString().trim();
                if (title.isEmpty()) {
                    input.setError(getContext().getString(R.string.prime_tab_name_required));
                    return;
                }
                mRepository.renameTab(tab.getId(), title);
                dialog.dismiss();
                refresh(parent);
                parent.onPrimeDrawerTabSelected();
            });
            input.requestFocus();
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
    }

    private void showDeleteTabDialog(FloatingHeaderView parent, PrimeDrawerTab tab) {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.prime_tab_delete_confirm_title)
                .setMessage(getContext().getString(
                        R.string.prime_tab_delete_confirm_message, tab.getTitle()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.prime_tab_delete, (dialog, which) -> {
                    mRepository.deleteTab(tab.getId());
                    refresh(parent);
                    parent.onPrimeDrawerTabSelected();
                })
                .show();
    }

    private void showCreateTabDialog(FloatingHeaderView parent) {
        EditText input = new EditText(getContext());
        input.setHint(R.string.prime_tab_name_hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        int horizontalPadding = dp(24);
        LinearLayout container = new LinearLayout(getContext());
        container.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        container.addView(input, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.prime_tab_create)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.prime_tab_create_action, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                String title = input.getText().toString().trim();
                if (title.isEmpty()) {
                    input.setError(getContext().getString(R.string.prime_tab_name_required));
                    return;
                }
                PrimeDrawerTab tab = mRepository.createTab(title);
                mRepository.setSelectedTab(tab.getId());
                dialog.dismiss();
                refresh(parent);
                parent.onPrimeDrawerTabSelected();
                post(() -> fullScroll(FOCUS_RIGHT));
            });
            input.requestFocus();
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
    }

    private TextView addPill(String label, boolean selected, @Nullable Integer selectedColor, Runnable action) {
        TextView pill = new TextView(getContext());
        pill.setText(label);
        pill.setGravity(Gravity.CENTER);
        pill.setMinHeight(dp(40));
        pill.setPadding(dp(16), 0, dp(16), 0);
        pill.setTextColor(Themes.getAttrColor(
                getContext(), selected ? android.R.attr.colorBackground : android.R.attr.textColorPrimary));

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(20));
        if (selected) {
            Integer modeTabColor = resolveModeTabColor();
            int defaultTabColor = resolveDefaultTabColor();
            background.setColor(selectedColor != null
                    ? selectedColor
                    : modeTabColor != null ? modeTabColor : defaultTabColor);
        } else {
            background.setColor(0x00000000);
            background.setStroke(dp(1),
                    Themes.getAttrColor(getContext(), android.R.attr.textColorSecondary));
        }
        pill.setBackground(background);
        pill.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(40));
        params.setMarginEnd(dp(8));
        mTabsContainer.addView(pill, params);
        return pill;
    }

    @Nullable
    private Integer resolveModeTabColor() {
        com.android.launcher3.InvariantDeviceProfile.GridOption grid =
                com.android.launcher3.InvariantDeviceProfile.INSTANCE.get(getContext()).closestProfile;
        return new PrimeDrawerModePreferences(getContext())
                .get(grid, PrimeDrawerMode.TABS)
                .getDefaultTabsColor();
    }

    private int resolveDefaultTabColor() {
        app.lawnchair.theme.color.ColorOption option = PreferenceManager2.getInstance(getContext())
                .getDrawerTabsColorBlocking();
        if (option instanceof app.lawnchair.theme.color.ColorOption.CustomColor) {
            return ((app.lawnchair.theme.color.ColorOption.CustomColor) option).getColor();
        }
        if (option == app.lawnchair.theme.color.ColorOption.WallpaperPrimary.INSTANCE) {
            android.app.WallpaperColors colors = android.app.WallpaperManager.getInstance(getContext())
                    .getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM);
            if (colors != null && colors.getPrimaryColor() != null) {
                return colors.getPrimaryColor().toArgb();
            }
            return 0xFF007FFF;
        }
        return Themes.getAttrColor(getContext(), android.R.attr.colorAccent);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public int getExpectedHeight() {
        return mPrefs.getDrawerTabsEnabled().get() ? dp(48) : 0;
    }

    @Override
    public boolean shouldDraw() {
        return mPrefs.getDrawerTabsEnabled().get();
    }

    @Override
    public boolean hasVisibleContent() {
        return shouldDraw();
    }

    @Override
    public void setVerticalScroll(int scroll, boolean isScrolledOut) {
        // Prime category tabs are navigation, not disposable floating content. FloatingHeaderView
        // moves upward as the app list scrolls, so cancel that movement here to keep this row
        // pinned at the top of the drawer instead of scrolling out with prediction/header rows.
        int headerTranslation = mHeaderParent != null
                ? Math.round(mHeaderParent.getTranslationY())
                : scroll;
        setTranslationY(-headerTranslation);
        mIsScrolledOut = false;
        setVisibility(mPrefs.getDrawerTabsEnabled().get() ? VISIBLE : GONE);
    }

    @Override
    public Class<PrimeDrawerTabsView> getTypeClass() {
        return PrimeDrawerTabsView.class;
    }

    @Override
    public View getFocusedChild() {
        return null;
    }
}
