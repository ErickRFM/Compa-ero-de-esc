package org.companerodeescuela.feature.classroom

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.shared.contracts.ClassInvite
import org.companerodeescuela.shared.contracts.ClassroomSummary
import org.companerodeescuela.shared.contracts.UserRole

@Composable
fun ClassroomScreen(
    roles: Set<UserRole>,
    modifier: Modifier = Modifier,
    viewModel: ClassroomViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isStudent = UserRole.STUDENT in roles
    val canCreate = UserRole.TEACHER in roles || roles.any(UserRole::isAdministrative)
    val pendingTeacher = UserRole.TEACHER_PENDING in roles

    var joinCode by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = CompaneroSize.homeContentMaxWidth)
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = CompaneroSpacing.page,
                vertical = CompaneroSpacing.sm,
            ),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
    ) {
        Text(
            text = "Mis clases",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Clases de Compañero funcionan sin depender del sistema escolar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.loading && state.classrooms.isEmpty()) {
            CircularProgressIndicator()
        }

        state.errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.successMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (isStudent) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(CompaneroSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
                ) {
                    Text(
                        text = "Unirme a una clase",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Ingresa la clave que compartió tu docente. Las claves temporales vencen automáticamente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { raw ->
                            joinCode = raw
                                .uppercase()
                                .filter { it.isLetterOrDigit() || it == '-' }
                                .take(9)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Clave de clase") },
                        placeholder = { Text("ABCD-2345") },
                    )
                    Button(
                        onClick = { viewModel.join(joinCode) },
                        enabled = !state.submitting && joinCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Unirme")
                    }
                }
            }
        }

        if (pendingTeacher) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(CompaneroSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                ) {
                    Text(
                        text = "Cuenta docente pendiente",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Podrás crear clases y generar invitaciones después de verificar tu perfil docente.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (canCreate) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(CompaneroSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
                ) {
                    Text(
                        text = "Crear clase",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedTextField(
                        value = className,
                        onValueChange = { className = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Nombre de la clase") },
                    )
                    OutlinedTextField(
                        value = room,
                        onValueChange = { room = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Aula (opcional)") },
                    )
                    Button(
                        onClick = {
                            viewModel.createClassroom(
                                name = className,
                                description = null,
                                room = room.ifBlank { null },
                            )
                            className = ""
                            room = ""
                        },
                        enabled = !state.submitting && className.trim().length >= 2,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Crear clase")
                    }
                }
            }
        }

        state.activeInvite?.let { invite ->
            InviteCard(
                invite = invite,
                onCopy = { copyInvite(context, invite) },
                onShare = { shareInvite(context, invite) },
                onRevoke = viewModel::revokeInvite,
                enabled = !state.submitting,
            )
        }

        Text(
            text = "Tus clases",
            style = MaterialTheme.typography.titleMedium,
        )

        if (!state.loading && state.classrooms.isEmpty()) {
            Text(
                text = when {
                    pendingTeacher -> "Todavía no tienes clases. Tu perfil docente sigue pendiente de verificación."
                    isStudent -> "Todavía no te has unido a ninguna clase."
                    else -> "Todavía no tienes clases creadas."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.classrooms.forEachIndexed { index, classroom ->
            ClassroomCard(
                classroom = classroom,
                enabled = !state.submitting,
                onGenerateInvite = { viewModel.generateInvite(classroom.id) },
            )
            if (index != state.classrooms.lastIndex) {
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ClassroomCard(
    classroom: ClassroomSummary,
    enabled: Boolean,
    onGenerateInvite: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CompaneroSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
        ) {
            Text(
                text = classroom.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = buildString {
                    append(classroom.teacherDisplayName)
                    classroom.room?.let {
                        append(" · ")
                        append(it)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            classroom.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (classroom.canManage) {
                Button(
                    onClick = onGenerateInvite,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Generar clave · 30 min")
                }
            }
        }
    }
}

@Composable
private fun InviteCard(
    invite: ClassInvite,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRevoke: () -> Unit,
    enabled: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CompaneroSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
        ) {
            Text(
                text = "Invitación activa",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = invite.code,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Expira " + expiryLabel(invite.expiresAtEpochSeconds),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onCopy,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Copiar")
                }
                Button(
                    onClick = onShare,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Compartir")
                }
            }
            OutlinedButton(
                onClick = onRevoke,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Revocar clave")
            }
        }
    }
}

private fun copyInvite(context: Context, invite: ClassInvite) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(
        ClipData.newPlainText(
            "Clave de clase",
            invite.code,
        ),
    )
}

private fun shareInvite(context: Context, invite: ClassInvite) {
    val text = "Únete a mi clase en Compañero de Clase con la clave " +
        invite.code +
        ". La invitación es temporal."
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, "Compartir clave"))
}

private fun expiryLabel(epochSeconds: Long): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    return Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
