package com.android.launcher3.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.SandboxApplication
import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PrimePinnedDrawerShortcutsTest {
    @get:Rule val context = SandboxApplication()

    @Test
    fun backupImportRetainsOriginalIdentityAndAvoidsDuplicates() {
        context.getSharedPreferences("prime_pinned_drawer_shortcuts", 0).edit().clear().commit()
        val payload = """[{"id":"web-1","kind":"legacy","title":"Example",
            "intent":"#Intent;action=android.intent.action.VIEW;S.url=https://example.com;end",
            "icon":"base64-placeholder","webUrl":"https://example.com"}]"""
        PrimePinnedDrawerShortcuts.importBackup(context, payload)
        PrimePinnedDrawerShortcuts.importBackup(context, payload)
        val entries = JSONArray(PrimePinnedDrawerShortcuts.exportBackup(context))
        assertThat(entries.length()).isEqualTo(1)
        assertThat(entries.getJSONObject(0).getString("id")).isEqualTo("web-1")
        assertThat(entries.getJSONObject(0).getString("webUrl")).isEqualTo("https://example.com")
    }

    @Test
    fun malformedOrIncompleteEntriesAreIgnoredOnRestore() {
        context.getSharedPreferences("prime_pinned_drawer_shortcuts", 0).edit().clear().commit()
        PrimePinnedDrawerShortcuts.importBackup(
            context,
            """[{"id":"missing","title":"Incomplete","kind":"legacy"},
                {"id":"valid","title":"Valid","kind":"legacy",
                 "intent":"#Intent;action=android.intent.action.VIEW;end"}]"""
        )
        assertThat(JSONArray(PrimePinnedDrawerShortcuts.exportBackup(context)).length()).isEqualTo(1)
    }
}
