package org.companerodeescuela.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.companerodeescuela.core.designsystem.v8.V8FrostedGlassPanel
import org.companerodeescuela.shared.contracts.*

@Composable
fun StaffApplicationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StaffApplicationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.refresh() }
    DisposableEffect(viewModel) { onDispose { viewModel.clearDraft() } }
    StaffApplicationScreen(state, viewModel::submit, viewModel::refresh, onBack, modifier)
}

@Composable
fun StaffApplicationScreen(
    state: StaffApplicationUiState,
    onSubmit: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var reference by remember(state.ownerKey) { mutableStateOf("") }
    var preference by remember(state.ownerKey) { mutableStateOf("") }
    LaunchedEffect(state.submitted) {
        if (state.submitted) {
            reference = ""
            preference = ""
        }
    }
    AuthV8Layout(modifier, themeAware = true) {
        Text(stringResource(R.string.staff_request_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.staff_request_description), modifier = Modifier.padding(vertical = 12.dp), color = authMuted())
        V8FrostedGlassPanel(Modifier.fillMaxWidth(), themeAware = true) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.loading) CircularProgressIndicator(Modifier.testTag("staff_loading"))
                state.failure?.let { SecurityFailure(it) }
                for (application in state.applications) {
                    Text(
                        stringResource(
                            if (application.role ==
                                StaffRole.TUTOR
                            ) {
                                R.string.auth_role_tutor
                            } else {
                                R.string.auth_role_teacher
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            when (application.status) {
                                StaffApplicationStatus.PENDING -> R.string.staff_request_pending
                                StaffApplicationStatus.APPROVED -> R.string.staff_request_approved
                                StaffApplicationStatus.REJECTED -> R.string.staff_request_rejected
                            },
                        ),
                        modifier = Modifier.testTag("staff_application_status"),
                    )
                }
                if (state.canRequest) {
                    SecurityMemoryField(
                        reference,
                        { reference = it.take(160) },
                        R.string.staff_request_identity,
                        "staff_identity",
                        !state.working,
                    )
                    SecurityMemoryField(
                        preference,
                        { preference = it.take(120) },
                        R.string.staff_request_preference,
                        "staff_preference",
                        !state.working,
                    )
                    AuthV8PrimaryAction(
                        stringResource(R.string.staff_request_submit),
                        { onSubmit(reference, preference) },
                        reference.isNotBlank() && !state.working,
                        state.working,
                        Modifier.testTag("staff_submit"),
                        stringResource(R.string.mfa_working),
                    )
                } else if (!state.loading && state.failure == null && state.applications.isEmpty()) {
                    Text(stringResource(R.string.staff_request_unavailable))
                }
                TextButton(onRefresh, enabled = !state.working) { Text(stringResource(R.string.security_refresh)) }
            }
        }
        TextButton(onBack, modifier = Modifier.fillMaxWidth().testTag("security_back")) { Text(stringResource(R.string.security_back)) }
    }
}
