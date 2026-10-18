package app.lawnchair.qsb.providers

import com.android.launcher3.R

/** Qwant Web search, usable by both the dock search bar and search gestures. */
data object Qwant : QsbSearchProvider(
    id = "qwant",
    name = R.string.search_provider_qwant,
    packageName = "com.qwant.liberty",
    website = "https://www.qwant.com/",
    type = QsbSearchProviderType.WEBSITE,
)
