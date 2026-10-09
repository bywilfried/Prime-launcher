package app.lawnchair.theme

/**
 * Stable semantic IDs for user-editable theme colors.
 *
 * These IDs are intentionally UI/component agnostic. They are suitable for persistence and future
 * import/export, so renaming a Kotlin property or moving a view must not change an existing ID.
 */
enum class ThemeColorRole(val id: String, val section: Section) {
    GLOBAL_ACCENT("global.accent", Section.GLOBAL),
    GLOBAL_SETTINGS_BACKGROUND("global.settings.background", Section.GLOBAL),
    GLOBAL_SETTINGS_CARD_BACKGROUND("global.settings.card_background", Section.GLOBAL),
    GLOBAL_NOTIFICATION_DOT("global.notification_dot", Section.GLOBAL),
    GLOBAL_NOTIFICATION_DOT_TEXT("global.notification_dot_text", Section.GLOBAL),

    HOME_ICON_TEXT("home.icon_text", Section.HOME),
    HOME_FOLDER_ICON_TEXT("home.folder.icon_text", Section.HOME),
    HOME_HOTSEAT_BACKGROUND("home.hotseat.background", Section.DOCK),
    HOME_SEARCH_BACKGROUND("home.search.background", Section.DOCK),
    HOME_SEARCH_TEXT("home.search.text", Section.DOCK),
    HOME_SEARCH_HINT("home.search.hint", Section.DOCK),
    HOME_SEARCH_ICON("home.search.icon", Section.DOCK),
    HOME_SEARCH_BORDER("home.search.border", Section.DOCK),
    HOME_FOLDER_CLOSED_BACKGROUND("home.folder.closed.background", Section.HOME),
    HOME_FOLDER_CLOSED_TEXT("home.folder.closed.text", Section.HOME),
    HOME_FOLDER_OPEN_BACKGROUND("home.folder.open.background", Section.HOME),
    HOME_FOLDER_OPEN_TEXT("home.folder.open.text", Section.HOME),
    HOME_FOLDER_OPEN_HINT("home.folder.open.hint", Section.HOME),
    HOME_FOLDER_PAGINATION("home.folder.pagination", Section.HOME),
    HOME_FOLDER_BORDER("home.folder.border", Section.HOME),
    HOME_POPUP_BACKGROUND("home.popup.background", Section.HOME),
    HOME_POPUP_TEXT("home.popup.text", Section.HOME),
    HOME_POPUP_ICON("home.popup.icon", Section.HOME),

    // Dock-specific roles use independent IDs; legacy home.search IDs remain stable.
    DOCK_ICON_TEXT("dock.icon_text", Section.DOCK),
    DOCK_FOLDER_ICON_TEXT("dock.folder.icon_text", Section.DOCK),
    DOCK_FOLDER_CLOSED_BACKGROUND("dock.folder.closed.background", Section.DOCK),
    DOCK_FOLDER_CLOSED_TEXT("dock.folder.closed.text", Section.DOCK),
    DOCK_FOLDER_OPEN_BACKGROUND("dock.folder.open.background", Section.DOCK),
    DOCK_FOLDER_OPEN_TEXT("dock.folder.open.text", Section.DOCK),
    DOCK_FOLDER_OPEN_HINT("dock.folder.open.hint", Section.DOCK),
    DOCK_FOLDER_PAGINATION("dock.folder.pagination", Section.DOCK),
    DOCK_FOLDER_BORDER("dock.folder.border", Section.DOCK),

    DRAWER_BACKGROUND("drawer.background", Section.DRAWER),
    DRAWER_TEXT("drawer.text", Section.DRAWER),
    DRAWER_FOLDER_ICON_TEXT("drawer.folder.icon_text", Section.DRAWER),
    DRAWER_SEARCH_BACKGROUND_INACTIVE("drawer.search.background", Section.DRAWER),
    DRAWER_SEARCH_BACKGROUND_ACTIVE("drawer.search.background_active", Section.DRAWER),
    DRAWER_SEARCH_TEXT("drawer.search.text", Section.DRAWER),
    DRAWER_SEARCH_HINT("drawer.search.hint", Section.DRAWER),
    DRAWER_SEARCH_ICON("drawer.search.icon", Section.DRAWER),
    DRAWER_SEARCH_BORDER("drawer.search.border", Section.DRAWER),
    DRAWER_SEARCH_SELECTED_RESULT_BACKGROUND("drawer.search.selected_result.background", Section.DRAWER),
    DRAWER_FOLDER_CLOSED_BACKGROUND("drawer.folder.closed.background", Section.DRAWER),
    DRAWER_FOLDER_CLOSED_TEXT("drawer.folder.closed.text", Section.DRAWER),
    DRAWER_FOLDER_OPEN_BACKGROUND("drawer.folder.open.background", Section.DRAWER),
    DRAWER_FOLDER_OPEN_TEXT("drawer.folder.open.text", Section.DRAWER),
    DRAWER_FOLDER_OPEN_HINT("drawer.folder.open.hint", Section.DRAWER),
    DRAWER_FOLDER_PAGINATION("drawer.folder.pagination", Section.DRAWER),
    DRAWER_FOLDER_BORDER("drawer.folder.border", Section.DRAWER),
    DRAWER_PROFILE_TAB_SELECTED_BACKGROUND("drawer.profile_tab.selected.background", Section.DRAWER),
    DRAWER_PROFILE_TAB_UNSELECTED_BACKGROUND("drawer.profile_tab.unselected.background", Section.DRAWER),
    DRAWER_PROFILE_TAB_TEXT("drawer.profile_tab.text", Section.DRAWER),
    DRAWER_POPUP_BACKGROUND("drawer.popup.background", Section.DRAWER),
    DRAWER_POPUP_TEXT("drawer.popup.text", Section.DRAWER),
    DRAWER_POPUP_ICON("drawer.popup.icon", Section.DRAWER),

    TABS_CATEGORY_ACTIVE_BACKGROUND("tabs.category.active.background", Section.TABS),
    TABS_CATEGORY_INACTIVE_BACKGROUND("tabs.category.inactive.background", Section.TABS),
    TABS_CATEGORY_TEXT("tabs.category.text", Section.TABS),

    ;

    enum class Section {
        GLOBAL,
        HOME,
        DOCK,
        DRAWER,
        TABS,
    }

    companion object {
        private val byId = entries.associateBy(ThemeColorRole::id)

        fun fromId(id: String): ThemeColorRole? = byId[id]
    }
}
