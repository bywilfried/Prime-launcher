/*
 * Copyright 2022, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package app.lawnchair.ui.preferences.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupHeading
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupItem
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.navigation.AboutLicenses
import com.android.launcher3.BuildConfig
import com.android.launcher3.R

@Composable
fun About(
    modifier: Modifier = Modifier,
) {
    PreferenceLayoutLazyColumn(
        label = stringResource(id = R.string.about_label),
        modifier = modifier,
        backArrowVisible = !LocalIsExpandedScreen.current,
    ) {
        item { Spacer(Modifier.padding(top = 8.dp)) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_home_comp),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(CircleShape),
                )
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            Text(
                text = stringResource(id = R.string.derived_app_name),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                text = BuildConfig.VERSION_DISPLAY_NAME,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
        item {
            Text(
                text = "Prime Launcher est un fork de Launcher3 (AOSP), basé sur la branche de développement de Lawnchair 16.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
        item {
            PreferenceGroupHeading(stringResource(R.string.legal))
        }
        item {
            PreferenceGroupItem(
                cutTop = false,
                cutBottom = false,
            ) {
                NavigationActionPreference(
                    label = stringResource(id = R.string.acknowledgements),
                    destination = AboutLicenses,
                )
            }
        }
    }
}
