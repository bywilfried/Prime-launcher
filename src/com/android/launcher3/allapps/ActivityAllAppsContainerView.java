/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.allapps;

import static com.android.launcher3.Flags.enableExpandingPauseWorkButton;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.MAIN;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.SEARCH;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.WORK;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_PRIVATE_SPACE_HEADER;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_WORK_DISABLED_CARD;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_WORK_EDU_CARD;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_COUNT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_TAP_ON_PERSONAL_TAB;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_TAP_ON_WORK_TAB;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.ScrollableLayoutManager.PREDICTIVE_BACK_MIN_SCALE;
import static com.android.launcher3.views.RecyclerViewFastScroller.FastScrollerLocation.ALL_APPS_SCROLLER;
import static com.android.window.flags2.Flags.predictiveBackThreeButtonNav;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.view.PixelCopy;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Process;
import android.os.UserManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.CrossWindowBlurListeners;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.VisibleForTesting;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Flags;
import com.android.launcher3.Insettable;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.allapps.search.AllAppsSearchUiDelegate;
import com.android.launcher3.allapps.search.DefaultSearchAdapterProvider;
import com.android.launcher3.allapps.search.SearchAdapterProvider;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.keyboard.FocusedItemDecorator;
import com.android.launcher3.keyboard.ViewGroupFocusHelper;
import com.android.launcher3.model.StringCache;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.UserIconInfo;
import com.android.launcher3.recyclerview.AllAppsRecyclerViewPool;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.views.RecyclerViewFastScroller;
import com.android.launcher3.views.ScrimView;
import com.android.launcher3.views.SpringRelativeLayout;
import com.android.launcher3.workprofile.PersonalWorkSlidingTabStrip;
import com.android.systemui.plugins.AllAppsRow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import app.lawnchair.preferences2.PreferenceCacheExtensionsKt;
import static com.topjohnwu.superuser.internal.Utils.context;
import app.lawnchair.allapps.LawnchairAlphabeticalAppsList;
import app.lawnchair.font.FontManager;
import app.lawnchair.preferences.PreferenceManager;
import app.lawnchair.preferences2.PreferenceManager2;
import app.lawnchair.prime.drawer.PrimeDrawerTabsRepository;
import app.lawnchair.prime.drawer.PrimeDrawerTabsView;
import app.lawnchair.prime.drawer.PrimeDrawerVisualOverrides;
import app.lawnchair.theme.color.tokens.ColorTokens;
import app.lawnchair.util.LawnchairUtilsKt;
import app.lawnchair.ui.StretchRecyclerViewContainer;

/**
 * All apps container view with search support for use in a dragging activity.
 *
 * @param <T> Type of context inflating all apps.
 */
public class ActivityAllAppsContainerView<T extends Context & ActivityContext>
        extends SpringRelativeLayout implements DragSource, Insettable,
        OnDeviceProfileChangeListener, PersonalWorkSlidingTabStrip.OnActivePageChangedListener,
        ScrimView.ScrimDrawingController {


    private static final String TAG = "ActivityAllAppsContainerView";
    public static final float PULL_MULTIPLIER = .02f;
    public static final float FLING_VELOCITY_MULTIPLIER = 1200f;
    protected static final String BUNDLE_KEY_CURRENT_PAGE = "launcher.allapps.current_page";
    private static final long DEFAULT_SEARCH_TRANSITION_DURATION_MS = 300;
    // Render the header protection at all times to debug clipping issues.
    private static final boolean DEBUG_HEADER_PROTECTION = false;
    /** Context of an activity or window that is inflating this container. */

    protected final T mActivityContext;
    protected final List<AdapterHolder> mAH;
    protected final Predicate<ItemInfo> mPersonalMatcher = info -> {
        if (info == null) {
            return false;
        }
        if (Process.myUserHandle().equals(info.user)) {
            return true;
        }
        UserIconInfo userIconInfo = UserCache.getInstance(getContext()).getUserInfo(info.user);
        return userIconInfo.isCloned();
    }; // Lawnchair: Show app from clone profile
    protected WorkProfileManager mWorkManager;
    protected final PrivateProfileManager mPrivateProfileManager;
    protected final Point mFastScrollerOffset = new Point();
    protected int mScrimColor;
    protected final float mHeaderThreshold;
    protected final AllAppsSearchUiDelegate mSearchUiDelegate;

    // Used to animate Search results out and A-Z apps in, or vice-versa.
    private final SearchTransitionController mSearchTransitionController;
    private final Paint mHeaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect mInsets = new Rect();
    private final AllAppsStore<T> mAllAppsStore;
    private final RecyclerView.OnScrollListener mScrollListener =
            new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    updateHeaderScroll(recyclerView.computeVerticalScrollOffset());
                }
            };
    private final Paint mNavBarScrimPaint;
    private final int mHeaderProtectionColor;
    private final int mPrivateSpaceBottomExtraSpace;
    private final Path mTmpPath = new Path();
    private final RectF mTmpRectF = new RectF();
    protected AllAppsPagedView mViewPager;
    protected FloatingHeaderView mHeader;
    protected final List<AllAppsRow> mAdditionalHeaderRows = new ArrayList<>();
    protected View mBottomSheetBackground;
    protected RecyclerViewFastScroller mFastScroller;
    private ConstraintLayout mFastScrollLetterLayout;

    /**
     * View that defines the search box. Result is rendered inside {@link #mSearchRecyclerView}.
     */
    protected View mSearchContainer;
    protected SearchUiManager mSearchUiManager;
    protected boolean mUsingTabs;
    protected RecyclerViewFastScroller mTouchHandler;
    @Nullable private RecyclerView.SimpleOnItemTouchListener mPrimeDrawerSwipeListener;
    @Nullable private PrimeTabTransitionController mPrimeTabTransitionController;
    @Nullable private Integer mPrimeSwipeBackgroundColor;
    @Nullable private android.widget.FrameLayout mPrimeSwipeViewport;

    /** {@code true} when rendered view is in search state instead of the scroll state. */
    private boolean mIsSearching;
    private boolean mSearchExitInProgress;
    boolean showFastScroller;
    private boolean mRebindAdaptersAfterSearchAnimation;
    private int mNavBarScrimHeight = 0;
    public SearchRecyclerView mSearchRecyclerView;
    protected SearchAdapterProvider<?> mMainAdapterProvider;
    private View mBottomSheetHandleArea;
    private View mBottomSheetHandle;
    private boolean mHasWorkApps;
    private boolean mHasPrivateApps;
    private float[] mBottomSheetCornerRadii;
    private ScrimView mScrimView;
    private int mHeaderColor;
    private int mBottomSheetBackgroundColorBlurFallback;
    private int mBottomSheetBackgroundColorOverBlur;
    private int mBottomSheetBackgroundColorLegacy;
    private int mTabsProtectionAlpha;
    @Nullable private AllAppsTransitionController mAllAppsTransitionController;

    @Nullable private java.util.function.Consumer<Boolean> mCrossWindowBlurListener;

    private final PreferenceManager2 pref2;
    private final PreferenceManager pref;

    // LC-Note: Allapps cache colour
    private int mCachedBottomSheetBgColor;

    public ActivityAllAppsContainerView(Context context) {
        this(context, null);
    }

    public ActivityAllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActivityAllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivityContext = ActivityContext.lookupContext(context);
        mAllAppsStore = new AllAppsStore<>(mActivityContext);
        
        pref2 = PreferenceManager2.getInstance(mActivityContext);
        pref = PreferenceManager.getInstance(mActivityContext);
        
        mScrimColor = ColorTokens.AllAppsScrimColor.resolveColor(context);
        mHeaderThreshold = getResources().getDimensionPixelSize(
                R.dimen.dynamic_grid_cell_border_spacing);
        mHeaderProtectionColor = ColorTokens.AllAppsHeaderProtectionColor.resolveColor(context);

        mWorkManager = new WorkProfileManager(
                mActivityContext.getSystemService(UserManager.class),
                this,
                mActivityContext.getStatsLogManager(),
                UserCache.INSTANCE.get(mActivityContext));
        mPrivateProfileManager = new PrivateProfileManager(
                mActivityContext.getSystemService(UserManager.class),
                this,
                mActivityContext.getStatsLogManager(),
                UserCache.INSTANCE.get(mActivityContext));
        mPrivateSpaceBottomExtraSpace = context.getResources().getDimensionPixelSize(
                R.dimen.ps_extra_bottom_padding);
        mAH = Arrays.asList(null, null, null);
        mNavBarScrimPaint = new Paint();
        mNavBarScrimPaint.setColor(Themes.getNavBarScrimColor(mActivityContext));

        AllAppsStore.OnUpdateListener onAppsUpdated = this::onAppsUpdated;
        mAllAppsStore.addUpdateListener(onAppsUpdated);

        // This is a focus listener that proxies focus from a view into the list view.  This is to
        // work around the search box from getting first focus and showing the cursor.
        setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && getActiveRecyclerView() != null) {
                getActiveRecyclerView().requestFocus();
            }
        });
        mSearchUiDelegate = createSearchUiDelegate();
        initContent();

        mSearchTransitionController = new SearchTransitionController(this);
    }

    /** Creates the delegate for initializing search. */
    protected AllAppsSearchUiDelegate createSearchUiDelegate() {
        return new AllAppsSearchUiDelegate(this);
    }

    public AllAppsSearchUiDelegate getSearchUiDelegate() {
        return mSearchUiDelegate;
    }

    /**
     * Initializes the view hierarchy and internal variables. Any initialization which actually uses
     * these members should be done in {@link #onFinishInflate()}.
     * In terms of subclass initialization, the following would be parallel order for activity:
     *   initContent -> onPreCreate
     *   constructor/init -> onCreate
     *   onFinishInflate -> onPostCreate
     */
    protected void initContent() {
        showFastScroller = PreferenceCacheExtensionsKt.firstCached(pref2.getShowScrollbar());

        mMainAdapterProvider = mSearchUiDelegate.createMainAdapterProvider();

        mAH.set(AdapterHolder.MAIN, new AdapterHolder(AdapterHolder.MAIN,
                new LawnchairAlphabeticalAppsList<>(mActivityContext,
                        mAllAppsStore,
                        null,
                        mPrivateProfileManager)));
        mAH.set(AdapterHolder.WORK, new AdapterHolder(AdapterHolder.WORK,
                new LawnchairAlphabeticalAppsList<>(mActivityContext, mAllAppsStore, mWorkManager, null)));
        mAH.set(SEARCH, new AdapterHolder(SEARCH,
                new LawnchairAlphabeticalAppsList<>(mActivityContext, mAllAppsStore, null, null)));

        getLayoutInflater().inflate(R.layout.all_apps_content, this);
        mHeader = findViewById(R.id.all_apps_header);
        mAdditionalHeaderRows.clear();
        mAdditionalHeaderRows.addAll(getAdditionalHeaderRows());
        mBottomSheetBackground = findViewById(R.id.bottom_sheet_background);
        mBottomSheetHandleArea = findViewById(R.id.bottom_sheet_handle_area);
        mBottomSheetHandle = findViewById(R.id.bottom_sheet_handle);
        mSearchRecyclerView = findViewById(R.id.search_results_list_view);
        mFastScroller = findViewById(R.id.fast_scroller);
        mFastScroller.setPopupView(findViewById(R.id.fast_scroller_popup));
        mFastScroller.setVisibility(showFastScroller ? VISIBLE : INVISIBLE);
        mFastScrollLetterLayout = findViewById(R.id.scroll_letter_layout);
        setClipChildren(false);

        mSearchContainer = inflateSearchBar();
        if (!isSearchBarFloating()) {
            // Add the search box above everything else in this container (if the flag is enabled,
            // it's added to drag layer in onAttach instead).
            addView(mSearchContainer);
            // The search container is visually at the top of the all apps UI, and should thus be
            // focused by default. It's added to end of the children list, so it needs to be
            // explicitly marked as focused by default.
            mSearchContainer.setFocusedByDefault(true);
        }
        mSearchUiManager = (SearchUiManager) mSearchContainer;
    }

    public List<AllAppsRow> getAdditionalHeaderRows() {
        return List.of();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mAH.get(SEARCH).setup(mSearchRecyclerView,
                /* Filter out A-Z apps */ itemInfo -> false);
        rebindAdapters(true /* force */);
        float cornerRadius = Themes.getDialogCornerRadius(getContext());
        mBottomSheetCornerRadii = new float[]{
                cornerRadius,
                cornerRadius, // Top left radius in px
                cornerRadius,
                cornerRadius, // Top right radius in px
                0,
                0, // Bottom right
                0,
                0 // Bottom left
        };

        if (Flags.allAppsBlur()) {
            int layerFg = ColorTokens.shade_panel_fg_color.resolveColor(getContext());
            int layerBg = ColorTokens.shade_panel_bg_color.resolveColor(getContext());
            mBottomSheetBackgroundColorOverBlur = ColorUtils.compositeColors(layerFg, layerBg);
            mBottomSheetBackgroundColorBlurFallback = ColorTokens.BottomSheetBackgroundColorBlurFallback.resolveColor(getContext());
        }

        mBottomSheetBackgroundColorLegacy = ColorTokens.SurfaceDimColor.resolveColor(getContext());

        // LC-Note: Update our allapps cached colour
        updateBottomSheetBackgroundColor();

        updateBackgroundVisibility(mActivityContext.getDeviceProfile());
        mSearchUiManager.initializeSearch(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isSearchBarFloating()) {
            mActivityContext.getDragLayer().addView(mSearchContainer);
            mSearchUiDelegate.onInitializeSearchBar();
        }
        mActivityContext.addOnDeviceProfileChangeListener(this);
        if (Utilities.ATLEAST_S) {
            java.util.function.Consumer<Boolean> listener = enabled -> {
                if (updateBottomSheetBackgroundColor(enabled)) {
                    invalidate();
                }
            };
            mCrossWindowBlurListener = listener;
            UI_HELPER_EXECUTOR.execute(() -> {
                try {
                    CrossWindowBlurListeners.getInstance()
                            .addListener(MAIN_EXECUTOR, listener);
                } catch (Throwable t) {
                    // LC-Ignored
                }
            });
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mActivityContext.removeOnDeviceProfileChangeListener(this);
        if (mCrossWindowBlurListener != null) {
            java.util.function.Consumer<Boolean> listener = mCrossWindowBlurListener;
            UI_HELPER_EXECUTOR.execute(() -> {
                try {
                    CrossWindowBlurListeners.getInstance()
                            .removeListener(listener);
                } catch (Throwable t) {
                    // LC-Ignored
                }
            });
            mCrossWindowBlurListener = null;
        }
    }

    public SearchUiManager getSearchUiManager() {
        return mSearchUiManager;
    }

    public View getSearchView() {
        return mSearchContainer;
    }

    /** Invoke when the current search session is finished. */
    public void onClearSearchResult() {
        getMainAdapterProvider().clearHighlightedItem();
        animateToSearchState(false);
        rebindAdapters();
    }

    /**
     * Sets results list for search
     */
    public void setSearchResults(ArrayList<AdapterItem> results) {
        getMainAdapterProvider().clearHighlightedItem();
        if (getSearchResultList().setSearchResults(results)) {
            getSearchRecyclerView().onSearchResultsChanged();
        }
        if (results != null) {
            animateToSearchState(true);
        }
    }

    /**
     * Sets results list for search.
     *
     * @param searchResultCode indicates if the result is final or intermediate for a given query
     *                         since we can get search results from multiple sources.
     */
    public void setSearchResults(ArrayList<AdapterItem> results, int searchResultCode) {
        setSearchResults(results);
        mSearchUiDelegate.onSearchResultsChanged(results, searchResultCode);
    }

    private void animateToSearchState(boolean goingToSearch) {
        animateToSearchState(goingToSearch, DEFAULT_SEARCH_TRANSITION_DURATION_MS);
    }

    public void setAllAppsTransitionController(
            AllAppsTransitionController allAppsTransitionController) {
        mAllAppsTransitionController = allAppsTransitionController;
    }

    void animateToSearchState(boolean goingToSearch, long durationMs) {
        if (!mSearchTransitionController.isRunning() && goingToSearch == isSearching()) {
            return;
        }
        mSearchExitInProgress = !goingToSearch;
        mFastScroller.setVisibility(goingToSearch ? INVISIBLE : VISIBLE);
        if (goingToSearch) {
            // Fade out the button to pause work apps.
            mWorkManager.onActivePageChanged(SEARCH);
        } else if (mAllAppsTransitionController != null) {
            // If exiting search, revert predictive back scale on all apps
            mAllAppsTransitionController.animateAllAppsToNoScale();
            mFastScroller.setVisibility(showFastScroller ? VISIBLE : INVISIBLE);
        }
        mSearchTransitionController.animateToState(goingToSearch, durationMs,
                /* onEndRunnable = */ () -> {
                    mIsSearching = goingToSearch;
                    updateSearchResultsVisibility();
                    int previousPage = getCurrentPage();
                    if (mRebindAdaptersAfterSearchAnimation) {
                        rebindAdapters(false);
                        mRebindAdaptersAfterSearchAnimation = false;
                    }

                    if (goingToSearch) {
                        mSearchUiDelegate.onAnimateToSearchStateCompleted();
                        mSearchExitInProgress = false;
                    } else {
                        setSearchResults(null);
                        if (mViewPager != null) {
                            mViewPager.setCurrentPage(previousPage);
                        }
                        onActivePageChanged(previousPage);
                        mSearchExitInProgress = false;
                    }
                });
    }

    public boolean shouldContainerScroll(MotionEvent ev) {
        BaseDragLayer dragLayer = mActivityContext.getDragLayer();
        // If the MotionEvent is inside the search box, and the container keeps on receiving touch
        // input, container should move down.
        if (dragLayer.isEventOverView(mSearchContainer, ev)) {
            // If the touch was on the edit text, container should move down ONLY when edit text is
            // already at the top.
            View editText = mSearchUiManager.getEditText();
            if (editText != null && dragLayer.isEventOverView(editText, ev)) {
                boolean canScrollUp = editText.canScrollVertically(-1);
                return !canScrollUp;
            }
            return true;
        }
        // If the MotionEvent is inside the handle area, and the container keeps on receiving touch
        // input, container should move down.
        if (dragLayer.isEventOverView(mBottomSheetHandleArea, ev)) {
            return true;
        }
        AllAppsRecyclerView rv = getActiveRecyclerView();
        if (rv == null) {
            return true;
        }
        if (rv.getScrollbar() != null
                && rv.getScrollbar().getThumbOffsetY() >= 0
                && dragLayer.isEventOverView(rv.getScrollbar(), ev)) {
            return false;
        }
        // Scroll if not within the container view (e.g. over large-screen scrim).
        if (!dragLayer.isEventOverView(getVisibleContainerView(), ev)) {
            return true;
        }
        return rv.shouldContainerScroll(ev, dragLayer);
    }

    /**
     * Resets the UI to be ready for fresh interactions in the future. Exits search and returns to
     * A-Z apps list.
     *
     * @param animate Whether to animate the header during the reset (e.g. switching profile tabs).
     */
    public void reset(boolean animate) {
        reset(animate, true);
    }

    /**
     * Resets the UI to be ready for fresh interactions in the future.
     *
     * @param animate Whether to animate the header during the reset (e.g. switching profile tabs).
     * @param exitSearch Whether to force exit the search state and return to A-Z apps list.
     */
    public void reset(boolean animate, boolean exitSearch) {
        // Scroll Main and Work RV to top. Search RV is done in `resetSearch`.
        if (!PreferenceCacheExtensionsKt.firstCached(pref2.getRememberPosition())) {
            for (int i = 0; i < mAH.size(); i++) {
                if (i != SEARCH && mAH.get(i).mRecyclerView != null) {
                    mAH.get(i).mRecyclerView.scrollToTop();
                }
            }
        }
        if (mTouchHandler != null) {
            mTouchHandler.endFastScrolling();
        }
        if (mHeader != null && mHeader.getVisibility() == VISIBLE) {
            mHeader.reset(animate);
        }
        updateBackgroundVisibility(mActivityContext.getDeviceProfile());
        // Reset the base recycler view after transitioning home.
        updateHeaderScroll(0);
        if (exitSearch) {
            // Reset the search bar and search RV after transitioning home.
            MAIN_EXECUTOR.getHandler().post(mSearchUiManager::resetSearch);
        }
        if (isSearching()) {
            mWorkManager.reset();
        }
    }

    /**
     * Exits search and returns to A-Z apps list. Scroll to the private space header.
     */
    public void resetAndScrollToPrivateSpaceHeader() {
        // Animate to A-Z with 0 time to reset the animation with proper state management.
        // We can't rely on `animateToSearchState` with delay inside `resetSearch` because that will
        // conflict with following scrolling to bottom, so we need it with 0 time here.
        animateToSearchState(false, 0);

        MAIN_EXECUTOR.getHandler().post(() -> {
            // Reset the search bar after transitioning home.
            // When `resetSearch` is called after `animateToSearchState` is finished, the inside
            // `animateToSearchState` with delay is a just no-op and return early.
            mSearchUiManager.resetSearch();
            // Switch to the main tab
            switchToTab(ActivityAllAppsContainerView.AdapterHolder.MAIN);
            // Scroll to bottom
            if (mPrivateProfileManager != null) {
                mPrivateProfileManager.scrollForHeaderToBeVisibleInContainer(
                        getActiveAppsRecyclerView(),
                        getPersonalAppList().getAdapterItems(),
                        mPrivateProfileManager.getPsHeaderHeight(),
                        mActivityContext.getDeviceProfile().getAllAppsProfile().getCellHeightPx());
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        mSearchUiManager.preDispatchKeyEvent(event);
        return super.dispatchKeyEvent(event);
    }

    public String getDescription() {
        if (!mUsingTabs && isSearching()) {
            return getContext().getString(R.string.all_apps_search_results);
        } else {
            StringCache cache = mActivityContext.getStringCache();
            if (mUsingTabs) {
                if (cache != null) {
                    return isPersonalTab()
                            ? cache.allAppsPersonalTabAccessibility
                            : cache.allAppsWorkTabAccessibility;
                } else {
                    return isPersonalTab()
                            ? getContext().getString(R.string.all_apps_button_personal_label)
                            : getContext().getString(R.string.all_apps_button_work_label);
                }
            }
            return getContext().getString(R.string.all_apps_button_label);
        }
    }

    public boolean isSearching() {
        return mIsSearching;
    }

    /**
     * @return {@code true} if back gesture should exit search rather than change launcher state.
      */
    public boolean shouldBackExitSearch() {
        return isSearching();
    }

    @Override
    public void onActivePageChanged(int currentActivePage) {
        if (mSearchTransitionController.isRunning()) {
            // Will be called at the end of the animation.
            return;
        }
        if (currentActivePage != SEARCH) {
            mActivityContext.hideKeyboard();
        }
        if (mAH.get(currentActivePage).mRecyclerView != null) {
            mAH.get(currentActivePage).mRecyclerView.bindFastScrollbar(mFastScroller,
                    ALL_APPS_SCROLLER);
        }
        // Header keeps track of active recycler view to properly render header protection.
        mHeader.setActiveRV(currentActivePage);
        reset(true /* animate */, !isSearching() /* exitSearch */);

        mWorkManager.onActivePageChanged(currentActivePage);
    }

    protected void rebindAdapters() {
        rebindAdapters(false /* force */);
    }

    protected void rebindAdapters(boolean force) {
        Log.d(TAG, "rebindAdapters: force: " + force);
        if (mSearchTransitionController.isRunning()) {
            mRebindAdaptersAfterSearchAnimation = true;
            return;
        }
        updateSearchResultsVisibility();

        boolean showTabs = shouldShowTabs();
        if (showTabs == mUsingTabs && !force) {
            Log.d(TAG, "rebindAdapters: Not needed.");
            return;
        }

        // replaceAppsRVcontainer() needs to use both mUsingTabs value to remove the old view AND
        // showTabs value to create new view. Hence the mUsingTabs new value assignment MUST happen
        // after this call.
        replaceAppsRVContainer(showTabs);
        mUsingTabs = showTabs;

        mAllAppsStore.unregisterIconContainer(mAH.get(AdapterHolder.MAIN).mRecyclerView);
        mAllAppsStore.unregisterIconContainer(mAH.get(AdapterHolder.WORK).mRecyclerView);
        mAllAppsStore.unregisterIconContainer(mAH.get(AdapterHolder.SEARCH).mRecyclerView);

        final AllAppsRecyclerView mainRecyclerView;
        final AllAppsRecyclerView workRecyclerView;
        if (mUsingTabs) {
            mainRecyclerView = (AllAppsRecyclerView) mViewPager.getChildAt(0);
            workRecyclerView = (AllAppsRecyclerView) mViewPager.getChildAt(1);
            mAH.get(AdapterHolder.MAIN).setup(mainRecyclerView, mPersonalMatcher);
            mAH.get(AdapterHolder.WORK).setup(workRecyclerView, mWorkManager.getItemInfoMatcher());
            workRecyclerView.setId(R.id.apps_list_view_work);
            if (enableExpandingPauseWorkButton()
                    || FeatureFlags.ENABLE_EXPANDING_PAUSE_WORK_BUTTON.get()) {
                mAH.get(AdapterHolder.WORK).mRecyclerView.addOnScrollListener(
                        mWorkManager.newScrollListener());
            }
            mViewPager.getPageIndicator().setActiveMarker(AdapterHolder.MAIN);
            findViewById(R.id.tab_personal)
                    .setOnClickListener((View view) -> {
                        Log.d(TAG, "rebindAdapters: " + "Clicked personal tab.");
                        if (mViewPager.snapToPage(AdapterHolder.MAIN)) {
                            mActivityContext.getStatsLogManager().logger()
                                    .log(LAUNCHER_ALLAPPS_TAP_ON_PERSONAL_TAB);
                        }
                    });
            findViewById(R.id.tab_work)
                    .setOnClickListener((View view) -> {
                        Log.d(TAG, "rebindAdapters: " + "Clicked work tab.");
                        if (mViewPager.snapToPage(AdapterHolder.WORK)) {
                            mActivityContext.getStatsLogManager().logger()
                                    .log(LAUNCHER_ALLAPPS_TAP_ON_WORK_TAB);
                        }
                    });
            setDeviceManagementResources();
            if (mHeader.isSetUp()) {
                onActivePageChanged(mViewPager.getNextPage());
            }
        } else {
            mainRecyclerView = findViewById(R.id.apps_list_view);
            workRecyclerView = null;
            mAH.get(AdapterHolder.MAIN).setup(mainRecyclerView, mPersonalMatcher);
            mAH.get(AdapterHolder.WORK).mRecyclerView = null;
        }
        setUpCustomRecyclerViewPool(
                mainRecyclerView,
                workRecyclerView,
                mAllAppsStore.getRecyclerViewPool());
        setupHeader();

        if (isSearchBarFloating()) {
            // Keep the scroller above the search bar.
            RelativeLayout.LayoutParams scrollerLayoutParams =
                    (LayoutParams) mFastScroller.getLayoutParams();
            scrollerLayoutParams.bottomMargin = mSearchContainer.getHeight()
                    + getResources().getDimensionPixelSize(
                            R.dimen.fastscroll_bottom_margin_floating_search);
        }

        mAllAppsStore.registerIconContainer(mAH.get(AdapterHolder.MAIN).mRecyclerView);
        mAllAppsStore.registerIconContainer(mAH.get(AdapterHolder.WORK).mRecyclerView);
        mAllAppsStore.registerIconContainer(mAH.get(AdapterHolder.SEARCH).mRecyclerView);
    }

    /**
     * If {@link ENABLE_ALL_APPS_RV_PREINFLATION} is enabled, wire custom
     * {@link RecyclerView.RecycledViewPool} to main and work {@link AllAppsRecyclerView}.
     *
     * Then if {@link ALL_APPS_GONE_VISIBILITY} is enabled, update max pool size. This is because
     * all apps rv's hidden visibility is changed to {@link View#GONE} from {@link View#INVISIBLE),
     * thus we cannot rely on layout pass to update pool size.
     */
    private static void setUpCustomRecyclerViewPool(
            @NonNull AllAppsRecyclerView mainRecyclerView,
            @Nullable AllAppsRecyclerView workRecyclerView,
            @NonNull AllAppsRecyclerViewPool recycledViewPool) {
        final boolean hasWorkProfile = workRecyclerView != null;
        recycledViewPool.setHasWorkProfile(hasWorkProfile);
        mainRecyclerView.setRecycledViewPool(recycledViewPool);
        if (workRecyclerView != null) {
            workRecyclerView.setRecycledViewPool(recycledViewPool);
        }
        mainRecyclerView.updatePoolSize(hasWorkProfile);
    }

    private void replaceAppsRVContainer(boolean showTabs) {
        Log.d(TAG, "replaceAppsRVContainer: showTabs: " + showTabs);
        for (int i = AdapterHolder.MAIN; i <= AdapterHolder.WORK; i++) {
            AdapterHolder adapterHolder = mAH.get(i);
            if (adapterHolder.mRecyclerView != null) {
                adapterHolder.mRecyclerView.setLayoutManager(null);
                adapterHolder.mRecyclerView.setAdapter(null);
            }
        }
        View oldView = getAppsRecyclerViewContainer();
        int index = indexOfChild(oldView);
        removeView(oldView);
        int layout = showTabs ? R.layout.all_apps_tabs : R.layout.prime_all_apps_rv_layout;
        final View rvContainer = getLayoutInflater().inflate(layout, this, false);
        addView(rvContainer, index);
        if (showTabs) {
            mViewPager = (AllAppsPagedView) rvContainer;
            mViewPager.initParentViews(this);
            mViewPager.getPageIndicator().setOnActivePageChangedListener(this);
            mViewPager.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    @Px final int bottomOffsetPx =
                            (int) (ActivityAllAppsContainerView.this.getMeasuredHeight()
                                    * PREDICTIVE_BACK_MIN_SCALE);
                    outline.setRect(
                            0,
                            0,
                            view.getMeasuredWidth(),
                            view.getMeasuredHeight() + bottomOffsetPx);
                }
            });

            mWorkManager.reset();
            post(() -> mAH.get(AdapterHolder.WORK).applyPadding());
        } else {
            mWorkManager.detachWorkUtilityViews();
            mViewPager = null;
        }

        removeCustomRules(rvContainer);
        removeCustomRules(getSearchRecyclerView());
        if (isAppDrawerSearchBarHidden()) {
            layoutWithoutSearchContainer(rvContainer, showTabs);
            layoutWithoutSearchContainer(getSearchRecyclerView(), /* tabs= */ false);
        } else if (isSearchBarFloating()) {
            alignParentTop(rvContainer, showTabs);
            alignParentTop(getSearchRecyclerView(), /* tabs= */ false);
        } else {
            layoutBelowSearchContainer(rvContainer, showTabs);
            layoutBelowSearchContainer(getSearchRecyclerView(), /* tabs= */ false);
        }

        updateSearchResultsVisibility();
    }

    void setupHeader() {
        mAdditionalHeaderRows.forEach(row -> mHeader.onPluginDisconnected(row));

        boolean hideSearchBar = isAppDrawerSearchBarHidden();
        // Keep personal/work tabs visible when search is off; only hide the empty header shell.
        mHeader.setVisibility((hideSearchBar && !mUsingTabs) ? View.GONE : View.VISIBLE);
        boolean tabsHidden = !mUsingTabs;
        mHeader.setup(
                mAH.get(AdapterHolder.MAIN).mRecyclerView,
                mAH.get(AdapterHolder.WORK).mRecyclerView,
                (SearchRecyclerView) mAH.get(SEARCH).mRecyclerView,
                getCurrentPage(),
                tabsHidden);

        int padding = (hideSearchBar && !mUsingTabs) ? 0 : mHeader.getMaxTranslation();
        mAH.forEach(adapterHolder -> {
            adapterHolder.mPadding.top = padding;
            adapterHolder.applyPadding();
            if (adapterHolder.mRecyclerView != null) {
                adapterHolder.mRecyclerView.scrollToTop();
            }
        });
        mAdditionalHeaderRows.forEach(row -> mHeader.onPluginConnected(row, mActivityContext));

        removeCustomRules(mHeader);
        if (hideSearchBar) {
            layoutWithoutSearchContainer(mHeader, false /* includeTabsMargin */);
        } else if (isSearchBarFloating()) {
            alignParentTop(mHeader, false /* includeTabsMargin */);
        } else {
            layoutBelowSearchContainer(mHeader, false /* includeTabsMargin */);
        }
    }

    /**
     * Force header height update with an offset. Used by {@link UniversalSearchInputView} to
     * request {@link FloatingHeaderView} to update its maxTranslation for multiline search bar.
     */
    public void forceUpdateHeaderHeight(int offset) {
        mHeader.updateSearchBarOffset(offset);
    }

    @Override
    public void addChildrenForAccessibility(ArrayList<View> arrayList) {
        super.addChildrenForAccessibility(arrayList);
        if (!Flags.floatingSearchBar()) {
            // Searchbox container is visually at the top of the all apps UI but it's present in
            // end of the children list.
            // We need to move the searchbox to the top in a11y tree for a11y services to read the
            // all apps screen in same as visual order.
            arrayList.stream().filter(v -> v.getId() == R.id.search_container_all_apps)
                    .findFirst().ifPresent(v -> {
                        arrayList.remove(v);
                        arrayList.add(0, v);
                    });
        }
    }

    protected void updateHeaderScroll(int scrolledOffset) {
        if (isAppDrawerSearchBarHidden() && !mUsingTabs)
            return;
        
        // Check if tab container background should be shown
        boolean showTabContainerBackground = PreferenceCacheExtensionsKt.firstCached(
                pref2.getWorkProfileTabContainerBackground(), pref2);
        
        float prog = Utilities.boundToRange((float) scrolledOffset / mHeaderThreshold, 0f, 1f);
        int headerColor = getHeaderColor(prog);
        int tabsAlpha = (!showTabContainerBackground || mHeader.getPeripheralProtectionHeight(/* expectedHeight */ false) == 0) ? 0
                : (int) (Utilities.boundToRange(
                        (scrolledOffset + mHeader.mSnappedScrolledY) / mHeaderThreshold, 0f, 1f)
                        * 255);
        if (headerColor != mHeaderColor || mTabsProtectionAlpha != tabsAlpha) {
            mHeaderColor = headerColor;
            mTabsProtectionAlpha = tabsAlpha;
            invalidateHeader();
        }
        if (mSearchUiManager.getEditText() == null) {
            return;
        }

        boolean bgVisible = mSearchUiManager.getBackgroundVisibility();
        if (scrolledOffset == 0) {
            if (!isSearching()) {
                bgVisible = true;
            }
            // LC-Note: Match Pixel Launcher behavior by focusing
            // and showing the keyboard on scroll to top
            if (PreferenceCacheExtensionsKt.firstCached(pref2.getAutoShowKeyboardInDrawer())) {
                boolean isControllerAnimating = mAllAppsTransitionController != null
                        && (mAllAppsTransitionController.getProgress() > 0f
                        || mAllAppsTransitionController.getAllAppScale().isAnimating());
                boolean isSearchTransitioning = mSearchTransitionController.isRunning()
                        || mSearchExitInProgress;
                if (!isControllerAnimating && !isSearchTransitioning) {
                    ExtendedEditText editText = mSearchUiManager.getEditText();
                    if (editText != null && !editText.isFocused()) {
                        editText.showKeyboard();
                    }
                }
            }
        } else if (scrolledOffset > mHeaderThreshold) {
            bgVisible = false;
        }
        mSearchUiManager.setBackgroundVisibility(bgVisible, 1 - prog);
    }

    protected int getHeaderColor(float blendRatio) {
        if (!mActivityContext.getDeviceProfile().shouldShowAllAppsOnSheet()) {
            float opacity = mSearchContainer.getAlpha();
            var showHeaderBackground = PreferenceCacheExtensionsKt.firstCached(
                pref2.getAppDrawerSearchBarBackground(), pref2);
            if (showHeaderBackground) {
                opacity = pref.getDrawerOpacity().get();
            }
            opacity = MathUtils.clamp(opacity, 0f, 1f);
            return ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(getBackgroundColor(), mHeaderProtectionColor, blendRatio),
                    Math.round(opacity * 255));
        }
        return isBackgroundBlurEnabled()
                ? ColorUtils.setAlphaComponent(mHeaderProtectionColor, (int) (blendRatio * 255))
                : ColorUtils.blendARGB(getBackgroundColor(), mHeaderProtectionColor, blendRatio);
    }

    private int getBackgroundColor() {
        return mActivityContext.getDeviceProfile().shouldShowAllAppsOnSheet()
                ? getBottomSheetBackgroundColor() : mScrimColor;
    }

    // LC-Note: Hey! We cache this! see updateBottomSheetBackgroundColor() for more details.
    public int getPrimeDrawerOpaqueBackgroundColor() {
        return ColorUtils.setAlphaComponent(getBackgroundColor(), 255);
    }

    int getBottomSheetBackgroundColor() {
        if (mPrimeSwipeBackgroundColor != null) {
            return mPrimeSwipeBackgroundColor;
        }
        PrimeDrawerVisualOverrides overrides =
                new PrimeDrawerTabsRepository(getContext()).getSelectedTabVisualOverrides();
        if (overrides != null
                && (overrides.getDrawerBackgroundColor() != null
                        || overrides.getDrawerBackgroundOpacity() != null)) {
            int color = overrides.getDrawerBackgroundColor() != null
                    ? overrides.getDrawerBackgroundColor() : mCachedBottomSheetBgColor;
            float alpha = overrides.getDrawerBackgroundOpacity() != null
                    ? overrides.getDrawerBackgroundOpacity()
                    : Color.alpha(mCachedBottomSheetBgColor) / 255f;
            return ColorUtils.setAlphaComponent(color, Math.round(alpha * 255));
        }
        return mCachedBottomSheetBgColor;
    }

    // LC-Note: This is getBottomSheetBackgroundColor() in AOSP, but we refactor it to cache our prefs.
    private boolean updateBottomSheetBackgroundColor() {
        return updateBottomSheetBackgroundColor(mActivityContext.isAllAppsBackgroundBlurEnabled());
    }

    // LC-Note: For listener to avoid querying stale value.
    private boolean updateBottomSheetBackgroundColor(boolean blurEnabled) {
        int defaultColor;
        if (!Flags.allAppsBlur()) {
            defaultColor = mBottomSheetBackgroundColorLegacy;
        } else if (!blurEnabled) {
            defaultColor = mBottomSheetBackgroundColorBlurFallback;
        } else {
            defaultColor = mBottomSheetBackgroundColorOverBlur;
        }
        int newColor = LawnchairUtilsKt.getAllAppsBackgroundColor(mActivityContext, defaultColor);
        if (mCachedBottomSheetBgColor != newColor) {
            mCachedBottomSheetBgColor = newColor;
            return true;
        }
        return false;
    }

    boolean isBackgroundBlurEnabled() {
        return Flags.allAppsBlur() && mActivityContext.isAllAppsBackgroundBlurEnabled();
    }

    /**
     * @return true if the search bar is floating above this container (at the bottom of the screen)
     */
    protected boolean isSearchBarFloating() {
        return mSearchUiDelegate.isSearchBarFloating();
    }

    /**
     * Whether the <em>floating</em> search bar should appear as a small pill when not focused.
     * <p>
     * Note: This method mirrors one in LauncherState. For subclasses that use Launcher, it likely
     * makes sense to use that method to derive an appropriate value for the current/target state.
     */
    public boolean shouldFloatingSearchBarBePillWhenUnfocused() {
        return false;
    }

    /**
     * How far from the bottom of the screen the <em>floating</em> search bar should rest when the
     * IME is not present.
     * <p>
     * To hide offscreen, use a negative value.
     * <p>
     * Note: if the provided value is non-negative but less than the current bottom insets, the
     * insets will be applied. As such, you can use 0 to default to this.
     * <p>
     * Note: This method mirrors one in LauncherState. For subclasses that use Launcher, it likely
     * makes sense to use that method to derive an appropriate value for the current/target state.
     */
    public int getFloatingSearchBarRestingMarginBottom() {
        return 0;
    }

    /**
     * How far from the start of the screen the <em>floating</em> search bar should rest.
     * <p>
     * To use original margin, return a negative value.
     * <p>
     * Note: This method mirrors one in LauncherState. For subclasses that use Launcher, it likely
     * makes sense to use that method to derive an appropriate value for the current/target state.
     */
    public int getFloatingSearchBarRestingMarginStart() {
        DeviceProfile dp = mActivityContext.getDeviceProfile();
        return dp.allAppsLeftRightMargin + dp.getAllAppsIconStartMargin(mActivityContext);
    }

    /**
     * How far from the end of the screen the <em>floating</em> search bar should rest.
     * <p>
     * To use original margin, return a negative value.
     * <p>
     * Note: This method mirrors one in LauncherState. For subclasses that use Launcher, it likely
     * makes sense to use that method to derive an appropriate value for the current/target state.
     */
    public int getFloatingSearchBarRestingMarginEnd() {
        DeviceProfile dp = mActivityContext.getDeviceProfile();
        return dp.allAppsLeftRightMargin + dp.getAllAppsIconStartMargin(mActivityContext);
    }

    private boolean isAppDrawerSearchBarHidden() {
        return PreferenceCacheExtensionsKt.firstCached(pref2.getHideAppDrawerSearchBar());
    }

    private void layoutBelowSearchContainer(View v, boolean includeTabsMargin) {
        if (!(v.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }

        RelativeLayout.LayoutParams layoutParams = (LayoutParams) v.getLayoutParams();
        layoutParams.addRule(RelativeLayout.ALIGN_TOP, R.id.search_container_all_apps);

        int topMargin = getContext().getResources().getDimensionPixelSize(
                R.dimen.all_apps_header_top_margin);
        if (includeTabsMargin) {
            topMargin += getContext().getResources().getDimensionPixelSize(
                    R.dimen.all_apps_header_pill_height);
        }
        layoutParams.topMargin = topMargin;
    }

    private void alignParentTop(View v, boolean includeTabsMargin) {
        if (!(v.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }

        RelativeLayout.LayoutParams layoutParams = (LayoutParams) v.getLayoutParams();
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        layoutParams.topMargin =
                includeTabsMargin
                        ? getContext().getResources().getDimensionPixelSize(
                        R.dimen.all_apps_header_pill_height)
                        : 0;
    }

    private void removeCustomRules(View v) {
        if (!(v.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }

        RelativeLayout.LayoutParams layoutParams = (LayoutParams) v.getLayoutParams();
        layoutParams.removeRule(RelativeLayout.ABOVE);
        layoutParams.removeRule(RelativeLayout.ALIGN_TOP);
        layoutParams.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
    }

    private void layoutWithoutSearchContainer(View v, boolean includeTabsMargin) {
        if (!(v.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }

        RelativeLayout.LayoutParams layoutParams = (LayoutParams) v.getLayoutParams();
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        int topMargin = 0;
        if (mActivityContext.getDeviceProfile().shouldShowAllAppsOnSheet()) {
            // Clear the bottom-sheet drag handle when search is not reserving that space.
            topMargin = getContext().getResources().getDimensionPixelSize(
                    R.dimen.bottom_sheet_handle_area_height);
        }
        if (includeTabsMargin) {
            topMargin += getContext().getResources().getDimensionPixelSize(
                    R.dimen.all_apps_header_pill_height);
        }
        layoutParams.topMargin = topMargin;
    }

    protected BaseAllAppsAdapter<T> createAdapter(AlphabeticalAppsList<T> appsList) {
        return new AllAppsGridAdapter<>(mActivityContext, getLayoutInflater(), appsList,
                mMainAdapterProvider);
    }

    public boolean isInAllApps() {
        // TODO: Make this abstract
        return true;
    }

    /** Creates the adapter provider for the main section. */
    protected SearchAdapterProvider<?> createMainAdapterProvider() {
        return new DefaultSearchAdapterProvider(mActivityContext);
    }

    /**
     * Inflates the search bar
     */
    protected View inflateSearchBar() {
        return mSearchUiDelegate.inflateSearchBar();
    }

    /** The adapter provider for the main section. */
    public final SearchAdapterProvider<?> getMainAdapterProvider() {
        return mMainAdapterProvider;
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> sparseArray) {
        try {
            // Many slice view id is not properly assigned, and hence throws null
            // pointer exception in the underneath method. Catching the exception
            // simply doesn't restore these slice views. This doesn't have any
            // user visible effect because because we query them again.
            super.dispatchRestoreInstanceState(sparseArray);
        } catch (Exception e) {
            Log.e("AllAppsContainerView", "restoreInstanceState viewId = 0", e);
        }

        Bundle state = (Bundle) sparseArray.get(R.id.work_tab_state_id, null);
        if (state != null) {
            int currentPage = state.getInt(BUNDLE_KEY_CURRENT_PAGE, 0);
            if (currentPage == AdapterHolder.WORK && mViewPager != null) {
                mViewPager.setCurrentPage(currentPage);
                rebindAdapters();
            } else {
                reset(true);
            }
        }
    }

    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        super.dispatchSaveInstanceState(container);
        Bundle state = new Bundle();
        state.putInt(BUNDLE_KEY_CURRENT_PAGE, getCurrentPage());
        container.put(R.id.work_tab_state_id, state);
    }

    public AllAppsStore<T> getAppsStore() {
        return mAllAppsStore;
    }

    public WorkProfileManager getWorkManager() {
        return mWorkManager;
    }

    /** Returns whether Private Profile has been setup. */
    public boolean hasPrivateProfile() {
        return mHasPrivateApps;
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        for (AdapterHolder holder : mAH) {
            holder.mAdapter.setAppsPerRow(dp.numShownAllAppsColumns);
            holder.mAppsList.setNumAppsPerRowAllApps(dp.numShownAllAppsColumns);
            if (holder.mRecyclerView != null) {
                // Remove all views and clear the pool, while keeping the data same. After this
                // call, all the viewHolders will be recreated.
                holder.mRecyclerView.swapAdapter(holder.mRecyclerView.getAdapter(), true);
                holder.mRecyclerView.getRecycledViewPool().clear();
            }
        }
        updateBackgroundVisibility(dp);

        boolean needsInvalidate = false;
        int navBarScrimColor = Themes.getNavBarScrimColor(mActivityContext);
        if (mNavBarScrimPaint.getColor() != navBarScrimColor) {
            mNavBarScrimPaint.setColor(navBarScrimColor);
            needsInvalidate = true;
        }
        // LC-Note: Update our allapps cached colour
        if (updateBottomSheetBackgroundColor()) {
            needsInvalidate = true;
        }

        if (needsInvalidate) {
            invalidate();
        }
    }

    protected void updateBackgroundVisibility(DeviceProfile deviceProfile) {
        mBottomSheetBackground.setVisibility(
                deviceProfile.shouldShowAllAppsOnSheet() ? View.VISIBLE : View.GONE);
        // Note: The opaque sheet background and header protection are added in drawOnScrim.
        // For the taskbar entrypoint, the scrim is drawn by its abstract slide in view container,
        // so its header protection is derived from this scrim instead.
    }

    @VisibleForTesting
    public void onAppsUpdated() {
        Log.d(TAG, "onAppsUpdated; number of apps: " + mAllAppsStore.getApps().length);
        mHasWorkApps = Stream.of(mAllAppsStore.getApps())
                .anyMatch(mWorkManager.getItemInfoMatcher());
        mHasPrivateApps = Stream.of(mAllAppsStore.getApps())
                .anyMatch(mPrivateProfileManager.getItemInfoMatcher());
        if (!isSearching()) {
            rebindAdapters();
        }
        if (mHasWorkApps) {
            mWorkManager.reset();
        }
        if (mHasPrivateApps) {
            mPrivateProfileManager.reset();
        }

        mActivityContext.getStatsLogManager().logger()
                .withCardinality(mAllAppsStore.getApps().length)
                .log(LAUNCHER_ALLAPPS_COUNT);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // The AllAppsContainerView houses the QSB and is hence visible from the Workspace
        // Overview states. We shouldn't intercept for the scrubber in these cases.
        if (!isInAllApps()) {
            mTouchHandler = null;
            return false;
        }

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            AllAppsRecyclerView rv = getActiveRecyclerView();
            if (rv != null && rv.getScrollbar() != null
                    && rv.getScrollbar().isHitInParent(ev.getX(), ev.getY(), mFastScrollerOffset)) {
                mTouchHandler = rv.getScrollbar();
            } else {
                mTouchHandler = null;
            }
        }
        if (mTouchHandler != null) {
            return mTouchHandler.handleTouchEvent(ev, mFastScrollerOffset);
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isInAllApps()) {
            return false;
        }

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            AllAppsRecyclerView rv = getActiveRecyclerView();
            if (rv != null && rv.getScrollbar() != null
                    && rv.getScrollbar().isHitInParent(ev.getX(), ev.getY(), mFastScrollerOffset)) {
                mTouchHandler = rv.getScrollbar();
            } else {
                mTouchHandler = null;

            }
        }
        if (mTouchHandler != null) {
            mTouchHandler.handleTouchEvent(ev, mFastScrollerOffset);
            return true;
        }
        if (isSearching()
                && mActivityContext.getDragLayer().isEventOverView(getVisibleContainerView(), ev)) {
            // if in search state, consume touch event.
            return true;
        }
        return false;
    }

    /** The current active recycler view (A-Z list from one of the profiles, or search results). */
    public AllAppsRecyclerView getActiveRecyclerView() {
        if (isSearching()) {
            return getSearchRecyclerView();
        }
        return getActiveAppsRecyclerView();
    }

    /**
     * Immutable prepared state for a Prime drawer destination.
     *
     * This intentionally starts with the deterministic page geometry. The same object is retained
     * from preview preparation through the live handoff; later preparation stages can add the
     * filtered/ordered content without changing that ownership model.
     */
    public static final class PrimePreparedDrawerPage {
        public final String tabId;
        public final int appsPerRow;
        public final int sideMarginPx;
        public final int topPaddingPx;
        public final int viewportLeftPx;
        public final int viewportWidthPx;
        @Nullable public final PrimeDrawerVisualOverrides visualOverrides;
        @Nullable public final LawnchairAlphabeticalAppsList.PrimePreparedContent content;

        PrimePreparedDrawerPage(String tabId, int appsPerRow, int sideMarginPx,
                int topPaddingPx, int viewportLeftPx, int viewportWidthPx) {
            this(tabId, appsPerRow, sideMarginPx, topPaddingPx,
                    viewportLeftPx, viewportWidthPx, null, null);
        }

        PrimePreparedDrawerPage(String tabId, int appsPerRow, int sideMarginPx, int topPaddingPx,
                int viewportLeftPx, int viewportWidthPx,
                @Nullable PrimeDrawerVisualOverrides visualOverrides,
                @Nullable LawnchairAlphabeticalAppsList.PrimePreparedContent content) {
            this.tabId = tabId;
            this.appsPerRow = appsPerRow;
            this.sideMarginPx = sideMarginPx;
            this.topPaddingPx = topPaddingPx;
            this.viewportLeftPx = viewportLeftPx;
            this.viewportWidthPx = viewportWidthPx;
            this.visualOverrides = visualOverrides;
            this.content = content;
        }

        PrimePreparedDrawerPage withContent(
                @Nullable LawnchairAlphabeticalAppsList.PrimePreparedContent content) {
            return new PrimePreparedDrawerPage(tabId, appsPerRow, sideMarginPx, topPaddingPx,
                    viewportLeftPx, viewportWidthPx, visualOverrides, content);
        }
    }

    @Nullable private PrimePreparedDrawerPage mPrimePreparedSwipePage;
    @Nullable private AllAppsRecyclerView mPrimePromotedRecyclerView;
    @Nullable private AllAppsRecyclerView mPrimeCanonicalRecyclerView;
    @Nullable private AllAppsRecyclerView mPrimeDirectSelectionSparePage;
    private int mPrimeDirectSelectionGeneration;
    private final StringBuilder mPrimeSwipeDebugLog = new StringBuilder();

    private void appendPrimeSwipeDebug(String event, @Nullable AllAppsRecyclerView active,
            @Nullable AllAppsRecyclerView adjacent) {
        long t = android.os.SystemClock.uptimeMillis();
        mPrimeSwipeDebugLog.append(t).append(' ').append(event);
        appendPrimeSwipeViewDebug(" A", active);
        appendPrimeSwipeViewDebug(" B", adjacent);
        mPrimeSwipeDebugLog.append('\n');
        if (mPrimeSwipeDebugLog.length() > 24000) {
            mPrimeSwipeDebugLog.delete(0, mPrimeSwipeDebugLog.length() - 18000);
        }
    }

    private void appendPrimeSwipeViewDebug(String label, @Nullable AllAppsRecyclerView rv) {
        if (rv == null) {
            mPrimeSwipeDebugLog.append(label).append("=null");
            return;
        }
        int[] loc = new int[2];
        rv.getLocationOnScreen(loc);
        mPrimeSwipeDebugLog.append(label)
                .append("{id=").append(Integer.toHexString(System.identityHashCode(rv)))
                .append(" l=").append(rv.getLeft())
                .append(" w=").append(rv.getWidth())
                .append(" tx=").append(rv.getTranslationX())
                .append(" sx=").append(loc[0])
                .append(" pl=").append(rv.getPaddingLeft())
                .append(" pr=").append(rv.getPaddingRight());
        View first = rv.getChildCount() > 0 ? rv.getChildAt(0) : null;
        if (first != null) {
            int[] childLoc = new int[2];
            first.getLocationOnScreen(childLoc);
            mPrimeSwipeDebugLog.append(" c0l=").append(first.getLeft())
                    .append(" c0x=").append(first.getX())
                    .append(" c0sx=").append(childLoc[0]);
        }
        View icon = findPrimeDebugIcon(rv);
        if (icon != null) {
            int[] iconLoc = new int[2];
            icon.getLocationOnScreen(iconLoc);
            mPrimeSwipeDebugLog.append(" icon{id=")
                    .append(Integer.toHexString(System.identityHashCode(icon)))
                    .append(" l=").append(icon.getLeft())
                    .append(" x=").append(icon.getX())
                    .append(" sx=").append(iconLoc[0])
                    .append(" sy=").append(iconLoc[1])
                    .append(" w=").append(icon.getWidth())
                    .append(" h=").append(icon.getHeight())
                    .append(" pl=").append(icon.getPaddingLeft())
                    .append(" pr=").append(icon.getPaddingRight())
                    .append(" scrollX=").append(icon.getScrollX())
                    .append(" tx=").append(icon.getTranslationX())
                    .append(" scale=").append(icon.getScaleX())
                    .append(" pivot=").append(icon.getPivotX());
            if (icon instanceof com.android.launcher3.BubbleTextView) {
                com.android.launcher3.BubbleTextView bubble =
                        (com.android.launcher3.BubbleTextView) icon;
                mPrimeSwipeDebugLog.append(" iconSet=")
                        .append(bubble.getPrimeLastIconSetReason())
                        .append('@').append(bubble.getPrimeLastIconSetUptime());
                android.graphics.drawable.Drawable[] drawables =
                        bubble.getCompoundDrawables();
                android.graphics.drawable.Drawable drawable =
                        drawables != null && drawables.length > 1 ? drawables[1] : null;
                if (drawable != null) {
                    Rect bounds = drawable.getBounds();
                    mPrimeSwipeDebugLog.append(" drawable{id=")
                            .append(Integer.toHexString(System.identityHashCode(drawable)))
                            .append(" b=").append(bounds.left).append(',')
                            .append(bounds.top).append(',')
                            .append(bounds.right).append(',')
                            .append(bounds.bottom)
                            .append(" iw=").append(drawable.getIntrinsicWidth())
                            .append(" ih=").append(drawable.getIntrinsicHeight())
                            .append('}');
                }
            }
            mPrimeSwipeDebugLog.append('}');
        }
        mPrimeSwipeDebugLog.append('}');
    }

    @Nullable
    private View findPrimeDebugIcon(View root) {
        if (root instanceof com.android.launcher3.BubbleTextView) return root;
        if (!(root instanceof android.view.ViewGroup)) return null;
        android.view.ViewGroup group = (android.view.ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findPrimeDebugIcon(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    public String getPrimeSwipeDebugLog() {
        return mPrimeSwipeDebugLog.length() == 0
                ? "Aucun swipe Prime enregistré." : mPrimeSwipeDebugLog.toString();
    }

    public void clearPrimeSwipeDebugLog() {
        mPrimeSwipeDebugLog.setLength(0);
    }

    /**
     * Leaves persistent-page swipe mode before a direct tab selection. Direct selections still
     * use Lawnchair's canonical holder; swipe commits instead keep the arrived physical page.
     */
    public void resetPrimePersistentSwipePage() {
        if (mPrimePromotedRecyclerView == null || mPrimeCanonicalRecyclerView == null) return;
        mPrimePromotedRecyclerView.setVisibility(INVISIBLE);
        mPrimePromotedRecyclerView.setTranslationX(0f);
        mPrimeCanonicalRecyclerView.setVisibility(VISIBLE);
        mPrimeCanonicalRecyclerView.setTranslationX(0f);

        // The canonical holder may have served as the off-screen swipe preview after the last
        // promotion. Restore it to live-selection mode before a direct tab click refreshes it;
        // otherwise its fixed preview tab keeps filtering apps for the previous destination while
        // grid/background overrides correctly follow the newly selected tab.
        if (mPrimeCanonicalRecyclerView.getApps() instanceof LawnchairAlphabeticalAppsList) {
            ((LawnchairAlphabeticalAppsList<?>) mPrimeCanonicalRecyclerView.getApps())
                    .clearPrimePreviewTabIdForLiveSelection();
        }

        // A direct click makes the canonical holder active again. If the last swipe had
        // promoted the standalone page, that exact page becomes the spare for the next swipe.
        // The swipe listener consumes this generation change before preparing its next target, so
        // it can never keep the now-active canonical RecyclerView as both A and B.
        if (mPrimePromotedRecyclerView != mPrimeCanonicalRecyclerView) {
            mPrimeDirectSelectionSparePage = mPrimePromotedRecyclerView;
        } else {
            mPrimeDirectSelectionSparePage = null;
        }
        mPrimeDirectSelectionGeneration++;
        mPrimePromotedRecyclerView = null;
        mPrimeCanonicalRecyclerView = null;
    }

    /**
     * Returns the physical Prime page that is currently visible. Direct tab navigation must
     * refresh this page instead of resurrecting the canonical holder after a swipe promotion.
     */
    @Nullable
    public AllAppsRecyclerView getPrimeVisibleRecyclerView(@Nullable AllAppsRecyclerView fallback) {
        if (mPrimePromotedRecyclerView != null
                && mPrimePromotedRecyclerView.getVisibility() == VISIBLE) {
            return mPrimePromotedRecyclerView;
        }
        return fallback;
    }

    /** Resolves one immutable destination snapshot for preview and live handoff. */
    private PrimePreparedDrawerPage buildPrimePreparedDrawerPage(String tabId) {
        DeviceProfile grid = mActivityContext.getDeviceProfile();
        PrimeDrawerVisualOverrides overrides =
                new PrimeDrawerTabsRepository(getContext()).getTabVisualOverrides(tabId);
        int appsPerRow = overrides != null && overrides.getDrawerColumns() != null
                ? overrides.getDrawerColumns() : grid.numShownAllAppsColumns;
        float marginFactor = overrides != null && overrides.getDrawerHorizontalMargin() != null
                ? overrides.getDrawerHorizontalMargin() : 1f;
        float topFactor = overrides != null && overrides.getDrawerTopPadding() != null
                ? overrides.getDrawerTopPadding() : 1f;
        int sideMargin = Math.round(grid.allAppsLeftRightMargin * marginFactor);
        int topPadding = Math.round(grid.allAppsPadding.top * topFactor);
        if (isSearchBarFloating() && !grid.shouldShowAllAppsOnSheet()) {
            topPadding += getResources().getDimensionPixelSize(
                    R.dimen.all_apps_additional_top_padding_floating_search);
        }
        AllAppsRecyclerView liveRv = getActiveAppsRecyclerView();
        // Container side margin and RecyclerView bounds are separate layout layers. setPadding()
        // changes the container's content area; the RecyclerView itself remains MATCH_PARENT in
        // apps_list_view_container and keeps AdapterHolder's allAppsPadding. Do not shrink/offset
        // the preview RecyclerView a second time for the same destination margin.
        int viewportLeft = liveRv != null ? liveRv.getLeft() : 0;
        int viewportWidth = liveRv != null ? Math.max(1, liveRv.getWidth()) : 1;
        return new PrimePreparedDrawerPage(
                tabId, appsPerRow, sideMargin, topPadding, viewportLeft, viewportWidth,
                overrides, null);
    }

    private PrimePreparedDrawerPage preparePrimeSwipePage(String tabId) {
        if (mPrimePreparedSwipePage == null || !tabId.equals(mPrimePreparedSwipePage.tabId)) {
            mPrimePreparedSwipePage = buildPrimePreparedDrawerPage(tabId);
        }
        return mPrimePreparedSwipePage;
    }

    /** Applies the destination container geometry before its persistent page starts moving. */
    private void applyPreparedPrimePageGeometry(PrimePreparedDrawerPage preparedPage) {
        DeviceProfile grid = mActivityContext.getDeviceProfile();
        if (!grid.isVerticalBarLayout() || FeatureFlags.enableResponsiveWorkspace()) {
            setPadding(preparedPage.sideMarginPx, preparedPage.topPaddingPx,
                    preparedPage.sideMarginPx, 0);
        }
    }

    public PrimePreparedDrawerPage prepareSelectedPrimeDrawerPage() {
        String tabId = new PrimeDrawerTabsRepository(getContext())
                .getConfiguration().getSelectedTabId();
        return preparePrimeSwipePage(tabId);
    }

    /** Installs Prime horizontal tab swipes on the actual app-list area only. */
    public void applyPrimeDrawerVisualOverrides() {
        PrimeDrawerVisualOverrides overrides =
                new PrimeDrawerTabsRepository(getContext()).getSelectedTabVisualOverrides();
        Boolean scrollbarOverride = overrides != null ? overrides.getShowScrollbar() : null;
        showFastScroller = scrollbarOverride != null
                ? scrollbarOverride
                : PreferenceCacheExtensionsKt.firstCached(pref2.getShowScrollbar());
        if (!isSearching()) {
            mFastScroller.setVisibility(showFastScroller ? VISIBLE : INVISIBLE);
        }

        DeviceProfile grid = mActivityContext.getDeviceProfile();
        PrimePreparedDrawerPage preparedPage = prepareSelectedPrimeDrawerPage();
        if (!grid.isVerticalBarLayout() || FeatureFlags.enableResponsiveWorkspace()) {
            setPadding(preparedPage.sideMarginPx, preparedPage.topPaddingPx,
                    preparedPage.sideMarginPx, 0);
        }
    }

    private interface PrimeTabTransitionController {
        boolean animateTo(String tabId, boolean moveLeft, Runnable onCommit);
    }

    /**
     * Animates a direct Prime tab click through the same prepared persistent-page transition used
     * by horizontal swipe. Non-adjacent tabs are still one A -> B transition.
     */
    public boolean animatePrimeTabSelection(
            @NonNull String tabId, boolean moveLeft, @NonNull Runnable onCommit) {
        return mPrimeTabTransitionController != null
                && mPrimeTabTransitionController.animateTo(tabId, moveLeft, onCommit);
    }

    public void setPrimeDrawerSwipeListener(
            BooleanSupplier isSwipeEnabled,
            Function<Boolean, String> getPreviewTabId,
            Consumer<Float> onTransitionProgress,
            Consumer<Boolean> onCommit,
            Consumer<Boolean> onPreviewFinished) {
        if (mPrimeDrawerSwipeListener != null) {
            for (int type : new int[]{AdapterHolder.MAIN, AdapterHolder.WORK}) {
                AllAppsRecyclerView rv = mAH.get(type).mRecyclerView;
                if (rv != null) rv.removeOnItemTouchListener(mPrimeDrawerSwipeListener);
            }
        }

        final int touchSlop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
        mPrimeDrawerSwipeListener = new RecyclerView.SimpleOnItemTouchListener() {
            private float downX;
            private float downY;
            private boolean horizontalSwipe;
            private boolean validAppAreaGesture;
            private boolean previewStarted;
            private boolean swipeLeft;
            private AllAppsRecyclerView previewPage;
            private LawnchairAlphabeticalAppsList<T> previewAppsList;
            private BaseAllAppsAdapter<?> previewAdapter;
            private String previewTabId;
            private boolean previewDirectionLeft;
            private boolean transitionRunning;
            private boolean previewLayoutReady;
            private boolean transitionPending;
            private float pendingStartDx;
            private AllAppsRecyclerView pendingRv;
            private int startBackground;
            private int targetBackground;
            private int directSelectionGeneration = mPrimeDirectSelectionGeneration;
            // Keep the drawer background synchronized with the same A -> B progress as the
            // persistent pages. The previous diagnostic freeze is no longer needed now that the
            // post-promotion rebind causing the visible shift has been removed.
            private static final boolean PRIME_SWIPE_DIAG_FREEZE_BACKGROUND = false;

            private int resolvePreviewBackground(String tabId) {
                PrimeDrawerVisualOverrides overrides =
                        new PrimeDrawerTabsRepository(getContext()).getTabVisualOverrides(tabId);
                if (overrides != null
                        && (overrides.getDrawerBackgroundColor() != null
                                || overrides.getDrawerBackgroundOpacity() != null)) {
                    int color = overrides.getDrawerBackgroundColor() != null
                            ? overrides.getDrawerBackgroundColor() : mCachedBottomSheetBgColor;
                    float alpha = overrides.getDrawerBackgroundOpacity() != null
                            ? overrides.getDrawerBackgroundOpacity()
                            : Color.alpha(mCachedBottomSheetBgColor) / 255f;
                    return ColorUtils.setAlphaComponent(color, Math.round(alpha * 255));
                }
                return mCachedBottomSheetBgColor;
            }

            private boolean ensurePreviewPage(AllAppsRecyclerView rv, String tabId) {
                if (directSelectionGeneration != mPrimeDirectSelectionGeneration) {
                    directSelectionGeneration = mPrimeDirectSelectionGeneration;
                    AllAppsRecyclerView directSpare = mPrimeDirectSelectionSparePage;
                    if (directSpare != null && directSpare != rv) {
                        previewPage = directSpare;
                        if (directSpare.getApps() instanceof LawnchairAlphabeticalAppsList) {
                            previewAppsList =
                                    (LawnchairAlphabeticalAppsList<T>) directSpare.getApps();
                        }
                        if (directSpare.getAdapter() instanceof BaseAllAppsAdapter) {
                            previewAdapter = (BaseAllAppsAdapter<?>) directSpare.getAdapter();
                        }
                    }
                    previewTabId = null;
                    previewLayoutReady = false;
                    previewStarted = false;
                    transitionPending = false;
                    pendingRv = null;
                    mPrimeDirectSelectionSparePage = null;
                }
                android.view.ViewParent parent = rv.getParent();
                if (!(parent instanceof android.widget.FrameLayout)) return false;
                android.widget.FrameLayout viewport = (android.widget.FrameLayout) parent;
                if (viewport.getId() != R.id.apps_list_view_container) return false;
                mPrimeSwipeViewport = viewport;

                int type = rv == mAH.get(AdapterHolder.WORK).mRecyclerView
                        ? AdapterHolder.WORK : AdapterHolder.MAIN;

                // Keep one real adjacent page alive across gestures. Only its filtered tab changes.
                if (previewPage == null) {
                    previewAppsList = new LawnchairAlphabeticalAppsList<>(
                            mActivityContext,
                            mAllAppsStore,
                            type == AdapterHolder.WORK ? mWorkManager : null,
                            type == AdapterHolder.MAIN ? mPrivateProfileManager : null);
                    AdapterHolder createdHolder = new AdapterHolder(type, previewAppsList);
                    previewAdapter = createdHolder.mAdapter;
                    previewPage = new AllAppsRecyclerView(getContext());
                    previewPage.setApps(previewAppsList);
                    previewPage.setLayoutManager(createdHolder.mLayoutManager);
                    previewPage.setAdapter(previewAdapter);
                    previewPage.setHasFixedSize(true);
                    previewPage.setItemAnimator(null);
                    previewPage.setRecycledViewPool(new RecyclerView.RecycledViewPool());

                    // The swipe page must use exactly the same grid geometry as the live page.
                    // A standalone AdapterHolder does not go through the normal container setup /
                    // device-profile path, so explicitly mirror the effective column count before
                    // its first layout. Otherwise the two translated RecyclerViews can expose
                    // visibly different row/column geometry at their shared edge.
                    PrimePreparedDrawerPage preparedPage =
                            preparePrimeSwipePage(tabId);
                    // B must be laid out inside B's final container geometry before any visible
                    // horizontal motion. Previously the page content was prepared for B while its
                    // parent still used A's side/top geometry, so the same persistent page could
                    // visibly settle when the parent caught up.
                    applyPreparedPrimePageGeometry(preparedPage);
                    previewAdapter.setAppsPerRow(preparedPage.appsPerRow);
                    previewAdapter.setPrimePreparedVisualOverrides(
                            preparedPage.visualOverrides);
                    previewAppsList.setNumAppsPerRowAllApps(preparedPage.appsPerRow);

                    previewPage.setPadding(
                            rv.getPaddingLeft(), rv.getPaddingTop(),
                            rv.getPaddingRight(), rv.getPaddingBottom());
                    Rect clip = rv.getClipBounds();
                    if (clip != null) previewPage.setClipBounds(new Rect(clip));
                    viewport.addView(previewPage, new android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT));
                    // Either physical page can become active after a commit, so both must own
                    // the same swipe listener. This is what makes the arrived RecyclerView a real
                    // persistent page rather than a visual overlay over the old live page.
                    previewPage.addOnItemTouchListener(mPrimeDrawerSwipeListener);
                }

                if (!tabId.equals(previewTabId)) {
                    // This preview is persistent, while the live holder can recompute its padding
                    // after insets/search/private-space changes. Mirror the live viewport on every
                    // destination preparation so both pages expose exactly the same rows at handoff.
                    previewPage.setPadding(
                            rv.getPaddingLeft(), rv.getPaddingTop(),
                            rv.getPaddingRight(), rv.getPaddingBottom());
                    previewPage.setClipToPadding(rv.getClipToPadding());
                    previewPage.setOverScrollMode(rv.getOverScrollMode());
                    Rect liveClip = rv.getClipBounds();
                    previewPage.setClipBounds(liveClip != null ? new Rect(liveClip) : null);
                    previewPage.setTranslationX(0f);
                    previewPage.setTranslationY(0f);

                    PrimePreparedDrawerPage preparedPage =
                            preparePrimeSwipePage(tabId);
                    // Persistent pages alternate roles after every commit, so destination geometry
                    // must also be installed when reusing the former active page as the new spare.
                    applyPreparedPrimePageGeometry(preparedPage);
                    previewAdapter.setAppsPerRow(preparedPage.appsPerRow);
                    previewAdapter.setPrimePreparedVisualOverrides(
                            preparedPage.visualOverrides);
                    previewAppsList.setNumAppsPerRowAllApps(preparedPage.appsPerRow);

                    // Configure the target tab and the normal MAIN/WORK predicate before the one
                    // and only dataset rebuild. Without this, the preview list has a null filter
                    // and briefly lays out the wrong category/all-apps population.
                    Predicate<ItemInfo> previewFilter = type == AdapterHolder.WORK
                            ? mWorkManager.getItemInfoMatcher() : mPersonalMatcher;
                    previewLayoutReady = false;
                    previewAppsList.configurePrimePreview(tabId, previewFilter);
                    // Capture the exact model ordering materialized by the preview. The selected
                    // live list receives this immutable ordering at commit, so it does not make a
                    // second independent custom-order decision for the same destination.
                    PrimePreparedDrawerPage preparedPageWithContent =
                            preparePrimeSwipePage(tabId).withContent(
                                    previewAppsList.getPrimePreparedContent());
                    mPrimePreparedSwipePage = preparedPageWithContent;
                    // A reused preview can retain the previous category's scroll position.
                    // Always present a newly targeted Prime category from its top edge.
                    previewPage.stopScroll();
                    previewPage.scrollToTop();
                    previewTabId = tabId;
                    waitForPrimePreviewLayoutStable(tabId, -1, -1, 0);
                } else if (!previewPage.isLayoutRequested()) {
                    previewLayoutReady = true;
                }

                int width = Math.max(1, rv.getWidth());
                // Keep preview and live in one identical local coordinate system. The page's
                // off-screen side is represented only by translation; its base bounds remain
                // MATCH_PARENT exactly like the live RecyclerView. Mixing a predicted B layout
                // rectangle with a direction-dependent translation made the handoff error change
                // sign between left and right swipes.
                android.widget.FrameLayout.LayoutParams previewLp =
                        (android.widget.FrameLayout.LayoutParams) previewPage.getLayoutParams();
                previewLp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                previewLp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                previewLp.leftMargin = 0;
                previewLp.topMargin = 0;
                previewLp.rightMargin = 0;
                previewLp.bottomMargin = 0;
                previewPage.setLayoutParams(previewLp);
                previewPage.setTranslationY(rv.getTranslationY());
                previewPage.setTranslationX(swipeLeft ? width : -width);
                previewPage.setVisibility(VISIBLE);
                appendPrimeSwipeDebug("PREPARE " + tabId, rv, previewPage);
                return true;
            }

            private void waitForPrimePreviewLayoutStable(
                    String tabId, int previousIconLeft, int previousIconWidth, int stablePasses) {
                if (previewPage == null || !tabId.equals(previewTabId)) return;
                previewPage.postOnAnimation(() -> {
                    if (previewPage == null || !tabId.equals(previewTabId)) return;
                    View icon = findPrimeDebugIcon(previewPage);
                    int iconLeft = icon != null ? icon.getLeft() : -1;
                    int iconWidth = icon != null ? icon.getWidth() : -1;
                    boolean layoutSettled = !previewPage.isLayoutRequested()
                            && iconWidth > 0
                            && iconLeft == previousIconLeft
                            && iconWidth == previousIconWidth;
                    int nextStablePasses = layoutSettled ? stablePasses + 1 : 0;
                    appendPrimeSwipeDebug("SETTLE pass=" + nextStablePasses, pendingRv, previewPage);
                    if (nextStablePasses >= 2) {
                        previewLayoutReady = true;
                        startPendingTransitionIfReady();
                    } else {
                        waitForPrimePreviewLayoutStable(
                                tabId, iconLeft, iconWidth, nextStablePasses);
                    }
                });
            }

            private void startPendingTransitionIfReady() {
                if (!transitionPending || !previewLayoutReady || pendingRv == null
                        || transitionRunning || previewPage == null) {
                    return;
                }
                AllAppsRecyclerView rv = pendingRv;
                float startDx = pendingStartDx;
                transitionPending = false;
                pendingRv = null;
                previewStarted = true;
                transitionRunning = true;
                rv.stopScroll();
                appendPrimeSwipeDebug("START dx=" + startDx, rv, previewPage);
                setProgress(rv, startDx);
                finishPreview(rv, true, startDx);
            }

            private void clearPreview(AllAppsRecyclerView rv) {
                // Keep the adjacent page attached and warm for the next gesture. Hiding it is
                // enough; destroying/recreating the RecyclerView was the remaining source of
                // visible reconstruction during slow swipes.
                if (previewPage != null) {
                    previewPage.setVisibility(INVISIBLE);
                    previewPage.setTranslationX(0f);
                }
                rv.setTranslationX(0f);
                mPrimeSwipeBackgroundColor = null;
                previewStarted = false;
                previewLayoutReady = false;
                transitionPending = false;
                pendingRv = null;
                invalidate();
                if (mScrimView != null) mScrimView.invalidate();
            }

            private void promoteArrivedPage(AllAppsRecyclerView oldActivePage) {
                if (previewPage == null) return;

                AllAppsRecyclerView arrivedPage = previewPage;
                arrivedPage.setTranslationX(0f);
                arrivedPage.setVisibility(VISIBLE);

                oldActivePage.setTranslationX(0f);
                oldActivePage.setVisibility(INVISIBLE);

                mPrimePromotedRecyclerView = arrivedPage;
                mPrimeCanonicalRecyclerView =
                        oldActivePage == mAH.get(AdapterHolder.MAIN).mRecyclerView
                                || oldActivePage == mAH.get(AdapterHolder.WORK).mRecyclerView
                                ? oldActivePage : mPrimeCanonicalRecyclerView;

                // Swap roles instead of swapping pixels: the exact RecyclerView that the user
                // watched arrive remains on screen. The previous active page becomes the spare
                // page and will be reconfigured off-screen for the next destination.
                previewPage = oldActivePage;
                if (oldActivePage.getApps() instanceof LawnchairAlphabeticalAppsList) {
                    previewAppsList =
                            (LawnchairAlphabeticalAppsList<T>) oldActivePage.getApps();
                } else {
                    previewAppsList = null;
                }
                if (oldActivePage.getAdapter() instanceof BaseAllAppsAdapter) {
                    previewAdapter = (BaseAllAppsAdapter<?>) oldActivePage.getAdapter();
                } else {
                    previewAdapter = null;
                }
                previewTabId = null;
                previewLayoutReady = false;
                previewStarted = false;
                transitionPending = false;
                pendingRv = null;
                mPrimeSwipeBackgroundColor = null;
                invalidate();
                if (mScrimView != null) mScrimView.invalidate();
                appendPrimeSwipeDebug("PROMOTED", arrivedPage, previewPage);
                tracePrimePostPromotion(arrivedPage, previewPage, 0);
            }

            private void tracePrimePostPromotion(AllAppsRecyclerView activePage,
                    AllAppsRecyclerView sparePage, int frame) {
                if (frame >= 8 || activePage != mPrimePromotedRecyclerView) return;
                activePage.postOnAnimation(() -> {
                    if (activePage != mPrimePromotedRecyclerView) return;
                    appendPrimeSwipeDebug("POST_PROMOTE frame=" + frame, activePage, sparePage);
                    tracePrimePostPromotion(activePage, sparePage, frame + 1);
                });
            }

            private void setProgress(AllAppsRecyclerView rv, float dx) {
                if (!previewStarted || previewPage == null) return;
                int width = Math.max(1, rv.getWidth());
                float clampedDx = Math.max(-width, Math.min(width, dx));
                float progress = Math.min(1f, Math.abs(clampedDx) / width);

                onTransitionProgress.accept(progress);
                rv.setTranslationX(clampedDx);
                float targetStart = swipeLeft ? width : -width;
                previewPage.setTranslationX(targetStart + clampedDx);
                appendPrimeSwipeDebug("FRAME p=" + Math.round(progress * 1000f)
                        + " dx=" + clampedDx, rv, previewPage);
                if (!PRIME_SWIPE_DIAG_FREEZE_BACKGROUND) {
                    mPrimeSwipeBackgroundColor =
                            ColorUtils.blendARGB(startBackground, targetBackground, progress);
                    invalidate();
                    if (mScrimView != null) mScrimView.invalidate();
                }
            }

            private void finishPreview(AllAppsRecyclerView rv, boolean commit, float currentDx) {
                int width = Math.max(1, rv.getWidth());
                float from = Math.min(1f, Math.abs(currentDx) / width);

                ValueAnimator animator = ValueAnimator.ofFloat(from, commit ? 1f : 0f);
                animator.setDuration(180L);
                animator.addUpdateListener(animation -> {
                    float progress = (float) animation.getAnimatedValue();
                    float dx = (swipeLeft ? -1f : 1f) * width * progress;
                    setProgress(rv, dx);
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        transitionRunning = false;
                        if (commit && previewPage != null) {
                            // The arrived page is already the final visual page. Persist selection,
                            // then promote this exact RecyclerView; there is no preview->live reveal
                            // and therefore no handoff frame in which the grid can shift.
                            previewPage.setTranslationX(0f);
                            appendPrimeSwipeDebug("ARRIVED", rv, previewPage);
                            onCommit.accept(swipeLeft);
                            appendPrimeSwipeDebug("COMMITTED", rv, previewPage);
                            promoteArrivedPage(rv);
                            onPreviewFinished.accept(true);
                        } else {
                            clearPreview(rv);
                            onPreviewFinished.accept(false);
                        }
                    }
                });
                animator.start();
            }

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView recycler, @NonNull MotionEvent e) {
                AllAppsRecyclerView rv = (AllAppsRecyclerView) recycler;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (!isSwipeEnabled.getAsBoolean()) {
                            validAppAreaGesture = false;
                            horizontalSwipe = false;
                            previewStarted = false;
                            return false;
                        }
                        // Track the gesture in screen coordinates. The RecyclerView itself is
                        // translated during Prime swipe, so local MotionEvent X would move with
                        // the view and feed that translation back into the next dx calculation.
                        downX = e.getRawX();
                        downY = e.getRawY();
                        horizontalSwipe = false;
                        previewStarted = false;
                        transitionRunning = false;
                        previewLayoutReady = false;
                        transitionPending = false;
                        pendingRv = null;
                        validAppAreaGesture = e.getX() >= 0 && e.getX() <= rv.getWidth()
                                && e.getY() >= 0 && e.getY() <= rv.getHeight();
                        // The Prime category strip has its own HorizontalScrollView gesture.
                        // RecyclerView can geometrically extend underneath the floating header, so
                        // an event over that row may still reach this listener. Exclude the row in
                        // screen coordinates before Prime starts preparing/intercepting a page swipe.
                        PrimeDrawerTabsView primeTabs =
                                mHeader != null
                                        ? mHeader.findFixedRowByType(PrimeDrawerTabsView.class)
                                        : null;
                        if (validAppAreaGesture && primeTabs != null
                                && primeTabs.getVisibility() == View.VISIBLE) {
                            int[] tabsLocation = new int[2];
                            primeTabs.getLocationOnScreen(tabsLocation);
                            float rawX = e.getRawX();
                            float rawY = e.getRawY();
                            if (rawX >= tabsLocation[0]
                                    && rawX <= tabsLocation[0] + primeTabs.getWidth()
                                    && rawY >= tabsLocation[1]
                                    && rawY <= tabsLocation[1] + primeTabs.getHeight()) {
                                validAppAreaGesture = false;
                            }
                        }
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        if (!validAppAreaGesture) return false;
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;

                        // Prepare the adjacent page as soon as horizontal intent appears, before
                        // RecyclerView's touch slop is crossed. By the time Prime intercepts the
                        // gesture there should be no adapter construction left on the critical
                        // first swipe frame.
                        if (!horizontalSwipe
                                && Math.abs(dx) > 2f
                                && Math.abs(dx) > Math.abs(dy)) {
                            boolean directionLeft = dx < 0;
                            String targetTabId = getPreviewTabId.apply(directionLeft);
                            if (targetTabId != null
                                    && (!targetTabId.equals(previewTabId)
                                            || previewDirectionLeft != directionLeft)) {
                                if (previewPage != null) clearPreview(rv);
                                swipeLeft = directionLeft;
                                previewDirectionLeft = directionLeft;
                                startBackground = getBottomSheetBackgroundColor();
                                targetBackground = resolvePreviewBackground(targetTabId);
                                if (ensurePreviewPage(rv, targetTabId)) {
                                    previewTabId = targetTabId;
                                }
                            }
                        }

                        if (!horizontalSwipe
                                && Math.abs(dx) > touchSlop
                                && Math.abs(dx) > Math.abs(dy) * 1.05f) {
                            horizontalSwipe = true;
                            swipeLeft = dx < 0;
                            String targetTabId = getPreviewTabId.apply(swipeLeft);
                            if (targetTabId != null
                                    && targetTabId.equals(previewTabId)
                                    && previewPage != null) {
                                if (!PRIME_SWIPE_DIAG_FREEZE_BACKGROUND) {
                                    mPrimeSwipeBackgroundColor = startBackground;
                                }
                                // Lock the destination immediately, but do not spend the
                                // animation budget while a large preview is still laying out.
                                // The posted readiness callback starts this automatically as soon
                                // as the prepared RecyclerView has completed its next UI turn.
                                transitionPending = true;
                                pendingRv = rv;
                                pendingStartDx = dx;
                                startPendingTransitionIfReady();
                            }
                        }
                        return horizontalSwipe;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // A page can be prewarmed before Prime actually intercepts the gesture.
                        // Dispose it when the touch ends as a normal RecyclerView gesture.
                        if (!horizontalSwipe && previewPage != null) {
                            clearPreview(rv);
                        }
                        validAppAreaGesture = false;
                        return false;
                    default:
                        return false;
                }
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView recycler, @NonNull MotionEvent e) {
                AllAppsRecyclerView rv = (AllAppsRecyclerView) recycler;
                if (!horizontalSwipe || !validAppAreaGesture) return;

                if (e.getActionMasked() == MotionEvent.ACTION_UP
                        || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    // Once recognized, the automatic transition is independent from the rest of
                    // this touch sequence. UP/CANCEL only releases gesture ownership.
                    horizontalSwipe = false;
                    validAppAreaGesture = false;
                    if (!transitionRunning && previewStarted) {
                        finishPreview(rv, false, e.getRawX() - downX);
                    }
                }
            }
        };

        mPrimeTabTransitionController = (tabId, moveLeft, directCommit) -> {
            if (transitionRunning || transitionPending || tabId == null) return false;
            AllAppsRecyclerView fallback = mAH.get(AdapterHolder.MAIN).mRecyclerView;
            AllAppsRecyclerView rv = getPrimeVisibleRecyclerView(fallback);
            if (rv == null || rv.getWidth() == 0) return false;

            swipeLeft = moveLeft;
            previewDirectionLeft = moveLeft;
            startBackground = getBottomSheetBackgroundColor();
            targetBackground = resolvePreviewBackground(tabId);
            if (!ensurePreviewPage(rv, tabId) || previewPage == null) return false;

            previewTabId = tabId;
            previewStarted = true;
            transitionRunning = true;
            rv.stopScroll();
            if (!PRIME_SWIPE_DIAG_FREEZE_BACKGROUND) {
                mPrimeSwipeBackgroundColor = startBackground;
            }
            setProgress(rv, 0f);

            int width = Math.max(1, rv.getWidth());
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(220L);
            animator.addUpdateListener(animation -> {
                float progress = (float) animation.getAnimatedValue();
                setProgress(rv, (moveLeft ? -1f : 1f) * width * progress);
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    transitionRunning = false;
                    if (previewPage == null) return;
                    previewPage.setTranslationX(0f);
                    appendPrimeSwipeDebug("DIRECT_ARRIVED " + tabId, rv, previewPage);
                    directCommit.run();
                    promoteArrivedPage(rv);
                    onPreviewFinished.accept(true);
                }
            });
            animator.start();
            return true;
        };

        for (int type : new int[]{AdapterHolder.MAIN, AdapterHolder.WORK}) {
            AllAppsRecyclerView rv = mAH.get(type).mRecyclerView;
            if (rv != null) rv.addOnItemTouchListener(mPrimeDrawerSwipeListener);
        }
    }

    /** Run some code on all the recycler views. */
    protected void forAllRecyclerViews(Consumer<AllAppsRecyclerView> consumer) {
        for (AdapterHolder holder : mAH) {
            if (holder.mRecyclerView == null) {
                continue;
            }
            consumer.accept(holder.mRecyclerView);
        }
    }

    /** The current focus change listener in the search container. */
    public OnFocusChangeListener getSearchFocusChangeListener() {
        return mAH.get(AdapterHolder.SEARCH).mOnFocusChangeListener;
    }

    /** The current apps recycler view in the container. */
    private AllAppsRecyclerView getActiveAppsRecyclerView() {
        if (!mUsingTabs || isPersonalTab()) {
            return mAH.get(AdapterHolder.MAIN).mRecyclerView;
        } else {
            return mAH.get(AdapterHolder.WORK).mRecyclerView;
        }
    }

    /**
     * The container for A-Z apps (the ViewPager for main+work tabs, or main RV). This is currently
     * hidden while searching.
     */
    public ViewGroup getAppsRecyclerViewContainer() {
        return mViewPager != null ? mViewPager : findViewById(R.id.apps_list_view_container);
    }

    /** The RV for search results, which is hidden while A-Z apps are visible. */
    public SearchRecyclerView getSearchRecyclerView() {
        return mSearchRecyclerView;
    }

    protected boolean isPersonalTab() {
        return mViewPager == null || mViewPager.getNextPage() == 0;
    }

    /**
     * Switches the current page to the provided {@code tab} if tabs are supported, otherwise does
     * nothing.
     */
    public void switchToTab(int tab) {
        if (mUsingTabs) {
            mViewPager.setCurrentPage(tab);
        }
    }

    public LayoutInflater getLayoutInflater() {
        return mSearchUiDelegate.getLayoutInflater();
    }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean success) {}

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        DeviceProfile grid = mActivityContext.getDeviceProfile();

        applyAdapterSideAndBottomPaddings(grid);

        MarginLayoutParams mlp = (MarginLayoutParams) getLayoutParams();
        // Ignore left/right insets on tablet because we are already centered in-screen.
        if (grid.getDeviceProperties().isTablet()) {
            mlp.leftMargin = mlp.rightMargin = 0;
        } else {
            mlp.leftMargin = insets.left;
            mlp.rightMargin = insets.right;
        }
        setLayoutParams(mlp);

        if (!grid.isVerticalBarLayout() || FeatureFlags.enableResponsiveWorkspace()) {
            int topPadding = grid.allAppsPadding.top;
            if (isSearchBarFloating() && !grid.shouldShowAllAppsOnSheet()) {
                topPadding += getResources().getDimensionPixelSize(
                        R.dimen.all_apps_additional_top_padding_floating_search);
            }
            setPadding(grid.allAppsLeftRightMargin, topPadding, grid.allAppsLeftRightMargin, 0);
        }
        InsettableFrameLayout.dispatchInsets(this, insets);
    }

    /**
     * Returns a padding in case a scrim is shown on the bottom of the view and a padding is needed.
     */
    protected int computeNavBarScrimHeight(WindowInsets insets) {
        return 0;
    }

    /**
     * Returns the current height of nav bar scrim
     */
    public int getNavBarScrimHeight() {
        return mNavBarScrimHeight;
    }

    @Override
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        mNavBarScrimHeight = computeNavBarScrimHeight(insets);
        applyAdapterSideAndBottomPaddings(mActivityContext.getDeviceProfile());
        return super.dispatchApplyWindowInsets(insets);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mNavBarScrimHeight > 0) {
            float left = (getWidth() - getWidth() / getScaleX()) / 2;
            float top = getHeight() / 2f + (getHeight() / 2f - mNavBarScrimHeight) / getScaleY();
            canvas.drawRect(left, top, getWidth() / getScaleX(),
                    top + mNavBarScrimHeight / getScaleY(), mNavBarScrimPaint);
        }
    }

    protected void updateSearchResultsVisibility() {
        if (isSearching()) {
            getSearchRecyclerView().setVisibility(VISIBLE);
            getAppsRecyclerViewContainer().setVisibility(GONE);
            mHeader.setVisibility(GONE);
        } else {
            getSearchRecyclerView().setVisibility(GONE);
            getAppsRecyclerViewContainer().setVisibility(VISIBLE);
            // Keep the empty header shell hidden when search is off and there are no tabs.
            mHeader.setVisibility(
                    (isAppDrawerSearchBarHidden() && !mUsingTabs) ? View.GONE : View.VISIBLE);
        }
        if (mHeader.isSetUp()) {
            mHeader.setActiveRV(getCurrentPage());
        }
    }

    private void applyAdapterSideAndBottomPaddings(DeviceProfile grid) {
        int bottomPadding = Math.max(mInsets.bottom, mNavBarScrimHeight);
        mAH.forEach(adapterHolder -> {
            adapterHolder.mPadding.bottom = bottomPadding;
            adapterHolder.mPadding.left = grid.allAppsPadding.left;
            adapterHolder.mPadding.right = grid.allAppsPadding.right;
            adapterHolder.applyPadding();
        });
    }

    private void setDeviceManagementResources() {
        if (mActivityContext.getStringCache() != null) {
            Button personalTab = findViewById(R.id.tab_personal);
            personalTab.setText(R.string.all_apps_personal_tab);
            personalTab.setAllCaps(false);
            FontManager.INSTANCE.get(getContext()).setCustomFont(personalTab, R.id.font_button);

            Button workTab = findViewById(R.id.tab_work);
            workTab.setText(R.string.all_apps_work_tab);
            workTab.setAllCaps(false);
            FontManager.INSTANCE.get(getContext()).setCustomFont(workTab, R.id.font_button);
        }
    }

    /**
     * Returns true if the container has work apps.
     */
    public boolean shouldShowTabs() {
        return mHasWorkApps;
    }

    // Used by tests only
    private boolean isDescendantViewVisible(int viewId) {
        final View view = findViewById(viewId);
        if (view == null) return false;

        if (!view.isShown()) return false;

        return view.getGlobalVisibleRect(new Rect());
    }

    /** Called in Launcher#bindStringCache() to update the UI when cache is updated. */
    public void updateWorkUI() {
        setDeviceManagementResources();
        if (mWorkManager.getWorkUtilityView() != null) {
            mWorkManager.getWorkUtilityView().updateStringFromCache();
        }
        inflateWorkCardsIfNeeded();
    }

    private void inflateWorkCardsIfNeeded() {
        AllAppsRecyclerView workRV = mAH.get(AdapterHolder.WORK).mRecyclerView;
        if (workRV != null) {
            for (int i = 0; i < workRV.getChildCount(); i++) {
                View currentView  = workRV.getChildAt(i);
                int currentItemViewType = workRV.getChildViewHolder(currentView).getItemViewType();
                if (currentItemViewType == VIEW_TYPE_WORK_EDU_CARD) {
                    ((WorkEduCard) currentView).updateStringFromCache();
                } else if (currentItemViewType == VIEW_TYPE_WORK_DISABLED_CARD) {
                    ((WorkPausedCard) currentView).updateStringFromCache();
                }
            }
        }
    }

    @VisibleForTesting
    public void setWorkManager(WorkProfileManager workManager) {
        mWorkManager = workManager;
    }

    @VisibleForTesting
    public boolean isPersonalTabVisible() {
        return isDescendantViewVisible(R.id.tab_personal);
    }

    @VisibleForTesting
    public boolean isWorkTabVisible() {
        return isDescendantViewVisible(R.id.tab_work);
    }

    public AlphabeticalAppsList<T> getSearchResultList() {
        return mAH.get(SEARCH).mAppsList;
    }

    public AlphabeticalAppsList<T> getPersonalAppList() {
        return mAH.get(MAIN).mAppsList;
    }

    public AlphabeticalAppsList<T> getWorkAppList() {
        return mAH.get(WORK).mAppsList;
    }

    public FloatingHeaderView getFloatingHeaderView() {
        return mHeader;
    }

    @VisibleForTesting
    public View getContentView() {
        return isSearching() ? getSearchRecyclerView() : getAppsRecyclerViewContainer();
    }

    /** The current page visible in all apps. */
    public int getCurrentPage() {
        return isSearching()
                ? SEARCH
                : mViewPager == null ? AdapterHolder.MAIN : mViewPager.getNextPage();
    }

    public PrivateProfileManager getPrivateProfileManager() {
        return mPrivateProfileManager;
    }

    /**
     * Adds an update listener to animator that adds springs to the animation.
     */
    public void addSpringFromFlingUpdateListener(ValueAnimator animator,
            float velocity /* release velocity */,
            float progress /* portion of the distance to travel*/) {
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                float distance = (1 - progress) * getHeight(); // px
                float settleVelocity = Math.min(0, distance
                        / (AllAppsTransitionController.INTERP_COEFF * animator.getDuration())
                        + velocity);
                absorbSwipeUpVelocity(Math.max(1000, Math.abs(
                        Math.round(settleVelocity * FLING_VELOCITY_MULTIPLIER))));
            }
        });
    }

    /** Invoked when the container is pulled. */
    public void onPull(float deltaDistance, float displacement) {
        absorbPullDeltaDistance(PULL_MULTIPLIER * deltaDistance, PULL_MULTIPLIER * displacement);
        // Current motion spec is to actually push and not pull
        // on this surface. However, until EdgeEffect.onPush (b/190612804) is
        // implemented at view level, we will simply pull
    }

    @Override
    public void getDrawingRect(Rect outRect) {
        super.getDrawingRect(outRect);
        outRect.offset(0, (int) getTranslationY());
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        invalidateHeader();
    }

    @Override
    public void setScaleY(float scaleY) {
        super.setScaleY(scaleY);
        try {
            if (predictiveBackThreeButtonNav() && mNavBarScrimHeight > 0) {
                // Call invalidate to prevent navbar scrim from scaling. The navbar scrim is drawn
                // directly onto the canvas. To prevent it from being scaled with the canvas, there's a
                // counter scale applied in dispatchDraw.
                invalidate(20, getHeight() - mNavBarScrimHeight, getWidth(), getHeight());
            }
        } catch (Throwable t) {
            // LC-Ignored
        }
    }

    /**
     * Set {@link Animator.AnimatorListener} on {@link mAllAppsTransitionController} to observe
     * animation of backing out of all apps search view to all apps view.
     */
    public void setAllAppsSearchBackAnimatorListener(Animator.AnimatorListener listener) {
        Preconditions.assertNotNull(mAllAppsTransitionController);
        if (mAllAppsTransitionController == null) {
            return;
        }
        mAllAppsTransitionController.setAllAppsSearchBackAnimationListener(listener);
    }

    public void setScrimView(ScrimView scrimView) {
        mScrimView = scrimView;
    }

    @Override
    public void drawOnScrimWithScaleAndBottomOffset(
            Canvas canvas, float scale, @Px int bottomOffsetPx) {
        final View panel = mBottomSheetBackground;
        final boolean hasBottomSheet = panel.getVisibility() == VISIBLE;
        final float translationY = ((View) panel.getParent()).getTranslationY();

        final float horizontalScaleOffset = (1 - scale) * panel.getWidth() / 2;
        final float verticalScaleOffset = (1 - scale) * (panel.getHeight() - getHeight() / 2);
        // Left and right insets can be applied to this container, as well as the panel.
        float left = getLeft() + panel.getLeft();
        float right = left + panel.getWidth();

        final float topNoScale = panel.getTop() + translationY;
        final float topWithScale = topNoScale + verticalScaleOffset;
        final float leftWithScale = left + horizontalScaleOffset;
        final float rightWithScale = right - horizontalScaleOffset;
        final float bottomWithOffset = panel.getBottom() + bottomOffsetPx;
        // Draw full background panel if presenting on a sheet.
        int bottomSheetBackgroundColor = getBottomSheetBackgroundColor();
        float bottomSheetBackgroundAlpha = Color.alpha(bottomSheetBackgroundColor) / 255.0f;
        if (hasBottomSheet) {
            mHeaderPaint.setColor(bottomSheetBackgroundColor);
            mHeaderPaint.setAlpha((int) (bottomSheetBackgroundAlpha * 255));

            mTmpRectF.set(
                    leftWithScale,
                    topWithScale,
                    rightWithScale,
                    bottomWithOffset);
            mTmpPath.reset();
            mTmpPath.addRoundRect(mTmpRectF, mBottomSheetCornerRadii, Direction.CW);
            canvas.drawPath(mTmpPath, mHeaderPaint);

            // When the background panel is blurred (or fallback), we don't add header protection.
            // TODO (b/414671116): Apply header protection whenever search bar is focused.
            if (Flags.allAppsBlur()) {
                return;
            }
        }

        if (DEBUG_HEADER_PROTECTION) {
            mHeaderPaint.setColor(Color.MAGENTA);
            mHeaderPaint.setAlpha(255);
        } else {
            mHeaderPaint.setColor(mHeaderColor);
            mHeaderPaint.setAlpha((int) (getAlpha() * Color.alpha(mHeaderColor)));
        }

        // If header is not visible or only differs from the background with alpha, don't draw it.
        int headerWithoutAlpha = ColorUtils.setAlphaComponent(mHeaderPaint.getColor(), 0);
        int backgroundWithoutAlpha = ColorUtils.setAlphaComponent(getBackgroundColor(), 0);
        if (headerWithoutAlpha == backgroundWithoutAlpha || mHeaderPaint.getColor() == 0) {
            return;
        }

        if (hasBottomSheet) {
            mHeaderPaint.setAlpha((int) (mHeaderPaint.getAlpha() * bottomSheetBackgroundAlpha));
        }

        // Draw header on background panel
        final float headerBottomNoScale =
                getHeaderBottom() + getVisibleContainerView().getPaddingTop();
        final float headerHeightNoScale = headerBottomNoScale - topNoScale;
        final float headerBottomWithScaleOnTablet = topWithScale + headerHeightNoScale * scale;
        final float headerBottomOffset = (getVisibleContainerView().getHeight() * (1 - scale) / 2);
        final float headerBottomWithScaleOnPhone = headerBottomNoScale * scale + headerBottomOffset;
        final FloatingHeaderView headerView = getFloatingHeaderView();
        if (hasBottomSheet) {
            // Start adding header protection if search bar or tabs will attach to the top.
            if (!isSearchBarFloating() || mUsingTabs) {
                mTmpRectF.set(
                        leftWithScale,
                        topWithScale,
                        rightWithScale,
                        headerBottomWithScaleOnTablet);
                mTmpPath.reset();
                mTmpPath.addRoundRect(mTmpRectF, mBottomSheetCornerRadii, Direction.CW);
                canvas.drawPath(mTmpPath, mHeaderPaint);
            }
        } else {
            canvas.drawRect(0, 0, canvas.getWidth(), headerBottomWithScaleOnPhone, mHeaderPaint);
        }

        // If tab exist (such as work profile), extend header with tab height
        final int tabsHeight = headerView.getPeripheralProtectionHeight(/* expectedHeight */ false);
        if (mTabsProtectionAlpha > 0 && tabsHeight != 0) {
            if (DEBUG_HEADER_PROTECTION) {
                mHeaderPaint.setColor(Color.BLUE);
                mHeaderPaint.setAlpha(255);
            } else {
                float tabAlpha = getAlpha() * mTabsProtectionAlpha;
                if (hasBottomSheet) {
                    tabAlpha *= bottomSheetBackgroundAlpha;
                }
                mHeaderPaint.setAlpha((int) tabAlpha);
            }
            left = 0f;
            right = canvas.getWidth();
            if (hasBottomSheet) {
                left = leftWithScale;
                right = rightWithScale;
            }

            final float tabTopWithScale = hasBottomSheet
                    ? headerBottomWithScaleOnTablet
                    : headerBottomWithScaleOnPhone;
            final float tabBottomWithScale = tabTopWithScale + tabsHeight * scale;

            canvas.drawRect(
                    left,
                    tabTopWithScale,
                    right,
                    tabBottomWithScale,
                    mHeaderPaint);
        }
    }

    /**
     * The height of the header protection as if the user scrolled down the app list.
     */
    float getHeaderProtectionHeight() {
        float headerBottom = getHeaderBottom() - getTranslationY();
        if (mUsingTabs) {
            return headerBottom + mHeader.getPeripheralProtectionHeight(/* expectedHeight */ true);
        } else {
            return headerBottom;
        }
    }

    ConstraintLayout getFastScrollerLetterList() {
        return mFastScrollLetterLayout;
    }

    /**
     * redraws header protection
     */
    public void invalidateHeader() {
        if (mScrimView != null) {
            mScrimView.invalidate();
        }
    }

    /** Returns the position of the bottom edge of the header */
    public int getHeaderBottom() {
        int bottom = (int) getTranslationY() + mHeader.getClipTop();
        if (isSearchBarFloating()) {
            if (mActivityContext.getDeviceProfile().shouldShowAllAppsOnSheet()) {
                return bottom + mBottomSheetBackground.getTop();
            }
            return bottom;
        }
        return bottom + mHeader.getTop();
    }

    boolean isUsingTabs() {
        return mUsingTabs;
    }

    /**
     * Returns a view that denotes the visible part of all apps container view.
     */
    public View getVisibleContainerView() {
        return mBottomSheetBackground.getVisibility() == VISIBLE ? mBottomSheetBackground : this;
    }

    protected void onInitializeRecyclerView(RecyclerView rv) {
        rv.addOnScrollListener(mScrollListener);
        mSearchUiDelegate.onInitializeRecyclerView(rv);
    }

    /** Returns the instance of @{code SearchTransitionController}. */
    public SearchTransitionController getSearchTransitionController() {
        return mSearchTransitionController;
    }

    /** Holds a {@link BaseAllAppsAdapter} and related fields. */
    public class AdapterHolder {
        public static final int MAIN = 0;
        public static final int WORK = 1;
        public static final int SEARCH = 2;

        private final int mType;
        public final BaseAllAppsAdapter<T> mAdapter;
        final RecyclerView.LayoutManager mLayoutManager;
        final AlphabeticalAppsList<T> mAppsList;
        final Rect mPadding = new Rect();
        AllAppsRecyclerView mRecyclerView;
        private OnFocusChangeListener mOnFocusChangeListener;

        AdapterHolder(int type, AlphabeticalAppsList<T> appsList) {
            mType = type;
            mAppsList = appsList;
            mAdapter = createAdapter(mAppsList);
            mAppsList.setAdapter(mAdapter);
            mLayoutManager = mAdapter.getLayoutManager();
        }

        void setup(@NonNull View rv, @Nullable Predicate<ItemInfo> matcher) {
            mAppsList.updateItemFilter(matcher);
            mRecyclerView = (AllAppsRecyclerView) rv;
            mRecyclerView.bindFastScrollbar(mFastScroller, ALL_APPS_SCROLLER);
            mRecyclerView.setEdgeEffectFactory(createEdgeEffectFactory());
            mRecyclerView.setApps(mAppsList);
            mRecyclerView.setLayoutManager(mLayoutManager);
            mRecyclerView.setAdapter(mAdapter);
            mRecyclerView.setHasFixedSize(true);
            // No animations will occur when changes occur to the items in this RecyclerView.
            mRecyclerView.setItemAnimator(null);
            onInitializeRecyclerView(mRecyclerView);
            // Use ViewGroupFocusHelper for SearchRecyclerView to draw focus outline for the
            // buttons in the view (e.g. query builder button and setting button)
            FocusedItemDecorator focusedItemDecorator = isSearch() ? new FocusedItemDecorator(
                    new ViewGroupFocusHelper(mRecyclerView)) : new FocusedItemDecorator(
                    mRecyclerView);
            mRecyclerView.addItemDecoration(focusedItemDecorator);
            // LC-Note: This is needed for highlight decoration
            if (isSearch()) {
                RecyclerView.ItemDecoration searchDecorator = getMainAdapterProvider().getDecorator();
                if (searchDecorator != null) {
                    mRecyclerView.addItemDecoration(searchDecorator);
                }
            }
            mOnFocusChangeListener = focusedItemDecorator.getFocusListener();
            mAdapter.setIconFocusListener(mOnFocusChangeListener);
            applyPadding();
        }

        void applyPadding() {
            if (mRecyclerView != null) {
                int bottomOffset = 0;
                if (isWork() && mWorkManager.getWorkUtilityView() != null) {
                    bottomOffset = mInsets.bottom + mWorkManager.getWorkUtilityView().getHeight();
                } else if (isMain() && mPrivateProfileManager != null) {
                    Optional<AdapterItem> privateSpaceHeaderItem = mAppsList.getAdapterItems()
                            .stream()
                            .filter(item -> item.viewType == VIEW_TYPE_PRIVATE_SPACE_HEADER)
                            .findFirst();
                    if (privateSpaceHeaderItem.isPresent()) {
                        bottomOffset = mPrivateSpaceBottomExtraSpace;
                    }
                }
                if (isSearchBarFloating()) {
                    bottomOffset += mSearchContainer.getHeight();
                }
                mRecyclerView.setPadding(mPadding.left, mPadding.top, mPadding.right,
                        mPadding.bottom + bottomOffset);
            }
        }

        private boolean isWork() {
            return mType == WORK;
        }

        private boolean isSearch() {
            return mType == SEARCH;
        }

        private boolean isMain() {
            return mType == MAIN;
        }
    }
}
