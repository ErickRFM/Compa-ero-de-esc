@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8PageTitle
import org.companerodeescuela.core.designsystem.v8.V8RedPrimaryButton
import org.companerodeescuela.core.designsystem.v8.V8ScreenHeader
import org.companerodeescuela.core.navigation.AppExperience

/** The caller supplies only server-authorized experiences for this distribution. */
@Composable
internal fun ExperiencePickerScreen(
    experiences: List<AppExperience>,
    selectionUnavailable: Boolean = false,
    onSelect: (AppExperience) -> Unit,
    onLogout: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = CompaneroSize.settingsContentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(CompaneroSpacing.page),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
        ) {
            V8ScreenHeader { V8PageTitle(stringResource(R.string.login_experience_picker_title)) }
            Text(
                text =
                    stringResource(
                        if (selectionUnavailable) R.string.login_experience_unavailable else R.string.login_experience_picker_description,
                    ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            V8GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
                    experiences.forEach { experience ->
                        V8RedPrimaryButton(
                            text = experienceLabel(experience),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelect(experience)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    onLogout?.let { logout ->
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                logout()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.role_unavailable_logout)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun experienceLabel(experience: AppExperience): String =
    stringResource(
        when (experience) {
            AppExperience.PRINCIPAL -> R.string.login_continue_principal
            AppExperience.STUDENT -> R.string.login_continue_student
            AppExperience.TEACHER -> R.string.login_continue_teacher
            AppExperience.TUTOR -> R.string.login_continue_tutor
            AppExperience.COORDINATOR -> R.string.login_continue_coordinator
            AppExperience.ADMIN -> R.string.login_continue_admin
            AppExperience.SUPER_ADMIN -> R.string.login_continue_super_admin
            AppExperience.GUEST -> R.string.login_continue_guest
            AppExperience.UNSUPPORTED -> R.string.login_continue
        },
    )
