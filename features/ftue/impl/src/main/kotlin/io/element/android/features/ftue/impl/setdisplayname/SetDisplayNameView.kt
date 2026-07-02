/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.ftue.impl.setdisplayname

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.ftue.impl.R
import io.element.android.libraries.designsystem.atomic.molecules.ButtonColumnMolecule
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.atomic.pages.HeaderFooterPage
import io.element.android.libraries.designsystem.background.OnboardingBackground
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun SetDisplayNameView(
    state: SetDisplayNameState,
    modifier: Modifier = Modifier,
) {
    HeaderFooterPage(
        modifier = modifier
            .statusBarsPadding()
            .fillMaxSize(),
        // Scrollable so the focused text field scrolls above the keyboard instead
        // of being hidden behind it (HeaderFooterPage applies imePadding itself).
        isScrollable = true,
        background = { OnboardingBackground() },
        header = { SetDisplayNameHeader(modifier = Modifier.padding(top = 60.dp, bottom = 28.dp)) },
        footer = { SetDisplayNameFooter(state) },
    ) {
        SetDisplayNameContent(state)
    }

    AsyncActionView(
        async = state.submitAction,
        onSuccess = {},
        onErrorDismiss = { state.eventSink(SetDisplayNameEvents.ClearError) },
    )
}

@Composable
private fun SetDisplayNameHeader(
    modifier: Modifier = Modifier,
) {
    IconTitleSubtitleMolecule(
        modifier = modifier,
        title = stringResource(R.string.screen_set_display_name_title),
        subTitle = stringResource(R.string.screen_set_display_name_subtitle),
        iconStyle = BigIcon.Style.Default(CompoundIcons.UserProfileSolid()),
    )
}

@Composable
private fun SetDisplayNameContent(state: SetDisplayNameState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.screen_set_display_name_label),
            value = state.displayName,
            singleLine = true,
            onValueChange = { state.eventSink(SetDisplayNameEvents.UpdateDisplayName(it)) },
        )
        if (state.isTooLong) {
            Text(
                text = stringResource(R.string.screen_set_display_name_too_long),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textCriticalPrimary,
            )
        }
    }
}

@Composable
private fun SetDisplayNameFooter(state: SetDisplayNameState) {
    ButtonColumnMolecule {
        Button(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(CommonStrings.action_continue),
            enabled = state.canSubmit,
            onClick = { state.eventSink(SetDisplayNameEvents.Submit) },
        )
    }
}

@PreviewsDayNight
@Composable
internal fun SetDisplayNameViewPreview(
    @PreviewParameter(SetDisplayNameStateProvider::class) state: SetDisplayNameState,
) {
    ElementPreview {
        SetDisplayNameView(state = state)
    }
}
