/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.touch;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.EDIT_MODE;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_ITEM_LONG_PRESSED;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.PopupWindow;
import android.graphics.Point;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;

import com.android.launcher3.AppWidgetResizeFrame;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import android.graphics.Rect;
import android.view.View;
import android.view.View.OnLongClickListener;

import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.celllayout.CellInfo;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.logging.StatsLogManager.StatsLogger;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.PrivateSpaceInstallAppButtonInfo;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.views.BubbleTextHolder;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.NavigableAppWidgetHostView;
import com.android.launcher3.widget.PendingItemDragHelper;
import com.android.launcher3.widget.WidgetCell;
import com.android.launcher3.widget.WidgetImageView;

/**
 * Class to handle long-clicks on workspace items and start drag as a result.
 */
public class ItemLongClickListener {

    public static final OnLongClickListener INSTANCE_WORKSPACE =
            ItemLongClickListener::onWorkspaceItemLongClick;

    public static final OnLongClickListener INSTANCE_ALL_APPS =
            ItemLongClickListener::onAllAppsItemLongClick;

    private static boolean onWorkspaceItemLongClick(View v) {
        if (v instanceof LauncherAppWidgetHostView) {
            TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "Widgets.onLongClick");
        } else {
            TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "onWorkspaceItemLongClick");
        }
        Launcher launcher = Launcher.getLauncher(v.getContext());
        if (!canStartDrag(launcher)) return false;
        if (!launcher.isInState(NORMAL)
                && !launcher.isInState(OVERVIEW)
                && !launcher.isInState(EDIT_MODE)) {
            return false;
        }
        if (!(v.getTag() instanceof ItemInfo)) return false;

        launcher.setWaitingForResult(null);
        if (v instanceof LauncherAppWidgetHostView
                && v.getTag() instanceof LauncherAppWidgetInfo) {
            ((LauncherAppWidgetHostView) v).armPrimeWidgetLongPress();
            return true;
        }
        beginDrag(v, launcher, (ItemInfo) v.getTag(), new DragOptions());
        return true;
    }

    /** Contextual widget actions, with small illustrative icons. */
    public static void showPrimeWidgetMenu(LauncherAppWidgetHostView widget,
            Launcher launcher, LauncherAppWidgetInfo info) {
        final PopupWindow popup = new PopupWindow(launcher);
        LinearLayout column = new LinearLayout(launcher);
        column.setOrientation(LinearLayout.VERTICAL);
        float density = launcher.getResources().getDisplayMetrics().density;
        int padding = Math.round(16 * density);
        column.setPadding(padding / 2, padding / 2, padding / 2, padding / 2);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(45, 48, 69));
        background.setCornerRadius(Math.round(26 * density));
        column.setBackground(background);
        column.setElevation(Math.round(8 * density));

        String[] labels = {"Marges du widget", "Redimensionner", "Déplacer"};
        int[] icons = {android.R.drawable.ic_menu_edit,
                android.R.drawable.ic_menu_crop, android.R.drawable.ic_menu_directions};
        for (int i = 0; i < labels.length; i++) {
            final int action = i;
            LinearLayout row = new LinearLayout(launcher);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(padding / 2, padding / 2, padding / 2, padding / 2);
            row.setMinimumHeight(Math.round(52 * density));
            ImageView image = new ImageView(launcher);
            image.setImageResource(icons[i]);
            image.setColorFilter(Color.rgb(205, 203, 222));
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    Math.round(24 * density), Math.round(24 * density));
            iconParams.rightMargin = padding;
            row.addView(image, iconParams);
            TextView label = new TextView(launcher);
            label.setText(labels[i]);
            label.setTextSize(15);
            label.setTextColor(Color.WHITE);
            row.addView(label);
            row.setOnClickListener(clicked -> {
                popup.dismiss();
                if (action == 0) {
                    AppWidgetResizeFrame.showPrimeWidgetMarginsDialog(widget);
                } else if (action == 1) {
                    if (widget.getParent() != null
                            && widget.getParent().getParent() instanceof com.android.launcher3.CellLayout) {
                        AppWidgetResizeFrame.showForWidget(widget,
                                (com.android.launcher3.CellLayout) widget.getParent().getParent());
                    }
                } else {
                    beginDrag(widget, launcher, info, new DragOptions());
                }
            });
            column.addView(row);
        }
        popup.setContentView(column);
        popup.setWidth(Math.round(250 * density));
        popup.setHeight(android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setBackgroundDrawable(background);
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setElevation(Math.round(10 * density));
        popup.showAsDropDown(widget, 0, -widget.getHeight());
    }

    public static void beginDrag(View v, Launcher launcher, ItemInfo info,
            DragOptions dragOptions) {
        if (info.container >= 0) {
            Folder folder = Folder.getOpen(launcher);
            if (folder != null) {
                if (!folder.getIconsInReadingOrder().contains(v)) {
                    folder.close(true);
                } else {
                    folder.startDrag(v, dragOptions);
                    return;
                }
            }
        }

        CellInfo longClickCellInfo = new CellInfo(v, info,
                launcher.getCellPosMapper().mapModelToPresenter(info));
        launcher.getWorkspace().startDrag(longClickCellInfo, dragOptions);
    }

    private static boolean onWidgetItemLongClick(WidgetCell v) {
        // Get the widget preview as the drag representation
        WidgetImageView image = v.getWidgetView();
        Launcher launcher = Launcher.getLauncher(v.getContext());
        DragSource dragSource = (target, dragObject, success) -> { };

        // If the ImageView doesn't have a drawable yet, the widget preview hasn't been loaded and
        // we abort the drag.
        if (image.getDrawable() == null && v.getAppWidgetHostViewPreview() == null) {
            return false;
        }

        PendingItemDragHelper dragHelper = new PendingItemDragHelper(v);
        // RemoteViews are being rendered in AppWidgetHostView in WidgetCell. And thus, the scale of
        // RemoteViews is equivalent to the AppWidgetHostView scale.
        dragHelper.setRemoteViewsPreview(v.getRemoteViewsPreview(), v.getAppWidgetHostViewScale());
        dragHelper.setAppWidgetHostViewPreview(v.getAppWidgetHostViewPreview());

        if (image.getDrawable() != null) {
            int[] loc = new int[2];
            launcher.getDragLayer().getLocationInDragLayer(image, loc);

            dragHelper.startDrag(image.getBitmapBounds(), image.getDrawable().getIntrinsicWidth(),
                    image.getWidth(), new Point(loc[0], loc[1]), dragSource, new DragOptions());
        } else {
            NavigableAppWidgetHostView preview = v.getAppWidgetHostViewPreview();
            int[] loc = new int[2];
            launcher.getDragLayer().getLocationInDragLayer(preview, loc);
            Rect r = new Rect();
            preview.getWorkspaceVisualDragBounds(r);
            dragHelper.startDrag(r, preview.getMeasuredWidth(), preview.getMeasuredWidth(),
                    new Point(loc[0], loc[1]), dragSource, new DragOptions());
        }
        return true;
    }

    private static boolean onAllAppsItemLongClick(View view) {
        if (view instanceof WidgetCell wc) {
            return onWidgetItemLongClick(wc);
        }
        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "onAllAppsItemLongClick");
        view.cancelLongPress();
        View v = (view instanceof BubbleTextHolder)
                ? ((BubbleTextHolder) view).getBubbleText()
                : view;
        Launcher launcher = Launcher.getLauncher(v.getContext());
        if (!canStartDrag(launcher)) return false;
        // When we have exited all apps or are in transition, disregard long clicks
        if (!launcher.isInState(ALL_APPS) && !launcher.isInState(OVERVIEW)) return false;
        if (launcher.getWorkspace().isSwitchingState()) return false;

        StatsLogger logger = launcher.getStatsLogManager().logger();
        if (v.getTag() instanceof ItemInfo itemInfo) {
            if (itemInfo instanceof PrivateSpaceInstallAppButtonInfo) {
                return false;
            }
            logger.withItemInfo((ItemInfo) v.getTag());
        }
        logger.log(LAUNCHER_ALLAPPS_ITEM_LONG_PRESSED);

        // Start the drag
        final DragController dragController = launcher.getDragController();
        dragController.addDragListener(new DragController.DragListener() {
            @Override
            public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
                v.setVisibility(INVISIBLE);
            }

            @Override
            public void onDragEnd() {
                v.setVisibility(VISIBLE);
                dragController.removeDragListener(this);
            }
        });

        launcher.getWorkspace().beginDragShared(v, launcher.getAppsView(), new DragOptions());
        return false;
    }

    public static boolean canStartDrag(Launcher launcher) {
        if (launcher == null) {
            return false;
        }
        // We prevent dragging when we are loading the workspace as it is possible to pick up a view
        // that is subsequently removed from the workspace in startBinding().
        if (launcher.isWorkspaceLocked()) return false;
        // Return early if an item is already being dragged (e.g. when long-pressing two shortcuts)
        if (launcher.getDragController().isDragging()) return false;
        // Return early if user is in the middle of selecting split-screen apps
        if (launcher.isSplitSelectionActive()) {
            return false;
        }

        return true;
    }
}
