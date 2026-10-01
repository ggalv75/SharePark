package com.sharepark.ui.screens.vehicles

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharepark.data.remote.cloud.SharedVehicleRepository
import com.sharepark.domain.model.Vehicle
import com.sharepark.ui.screens.vehicles.VehiclesViewModel.SharingState

/** Renders whichever sharing step [state] is at; nothing for [SharingState.Idle]. */
@Composable
fun SharingDialogs(
    state: SharingState,
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
    onJoin: (code: String) -> Unit,
    onCompleteJoin: (cloudId: String, name: String, localVehicleId: Long?) -> Unit
) {
    when (state) {
        SharingState.Idle -> Unit

        is SharingState.Working -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(state.message)
                }
            }
        )

        is SharingState.NeedsSignIn -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("התחברות לשיתוף רכב") },
            text = {
                Text(
                    "כדי שכמה אנשים יראו את אותו רכב, כל אחד צריך להתחבר עם חשבון Google. " +
                        "החשבון משמש רק לשיתוף — שאר האפליקציה ממשיכה לעבוד בלעדיו."
                )
            },
            confirmButton = { TextButton(onClick = onSignIn) { Text("התחבר עם Google") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
        )

        is SharingState.InviteReady -> InviteDialog(state, onDismiss)

        SharingState.EnterCode -> JoinDialog(onJoin = onJoin, onDismiss = onDismiss)

        is SharingState.ChooseLink -> LinkChoiceDialog(state, onCompleteJoin, onDismiss)

        is SharingState.Message -> AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text(state.text) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("אישור") } }
        )
    }
}

@Composable
private fun InviteDialog(state: SharingState.InviteReady, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val shareText = "הצטרפו לרכב \"${state.vehicleName}\" ב-SharePark כדי לראות איפה הוא חונה.\n" +
        "במסך הרכבים ← הצטרפות לרכב משותף, הזינו את הקוד: ${state.code}"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הזמנה ל\"${state.vehicleName}\"") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("שלחו את הקוד למי שמשתמש ברכב איתכם. הוא בתוקף ל-48 שעות.")
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.code,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    letterSpacing = 4.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(send, null))
            }) { Text("שלח קוד") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("סגור") } }
    )
}

@Composable
private fun JoinDialog(onJoin: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    val normalized = SharedVehicleRepository.normalizeCode(code)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הצטרפות לרכב משותף") },
        text = {
            Column {
                Text("הזינו את הקוד שקיבלתם ממי ששיתף את הרכב.")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(SharedVehicleRepository.CODE_LENGTH + 2) },
                    singleLine = true,
                    placeholder = { Text("ABCD2345") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onJoin(normalized) },
                enabled = normalized.length == SharedVehicleRepository.CODE_LENGTH
            ) { Text("הצטרף") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

/**
 * After joining: is this the same car you already registered with its Bluetooth (then this
 * phone detects its parkings too), or one you only want to watch?
 */
@Composable
private fun LinkChoiceDialog(
    state: SharingState.ChooseLink,
    onCompleteJoin: (cloudId: String, name: String, localVehicleId: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\"${state.name}\" — הרכב כבר רשום אצלך?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.candidates.isNotEmpty()) {
                    Text(
                        "אם כן, בחר אותו — והטלפון שלך יזהה ויעדכן גם הוא כשתחנה.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    state.candidates.forEach { vehicle ->
                        LinkOptionRow(
                            vehicle = vehicle,
                            onClick = { onCompleteJoin(state.cloudId, state.name, vehicle.id) }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                LinkOptionRow(
                    vehicle = null,
                    onClick = { onCompleteJoin(state.cloudId, state.name, null) }
                )
                Text(
                    "רוצה שגם הטלפון שלך יזהה חנייה? הוסף קודם את הרכב עם הבלוטות' שלו, ואז הצטרף שוב עם קוד חדש.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

@Composable
private fun LinkOptionRow(vehicle: Vehicle?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (vehicle != null) Icons.Default.DirectionsCar else Icons.Default.Visibility,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = vehicle?.name ?: "צפייה בלבד",
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = vehicle?.btName ?: "רואים איפה הרכב חונה, בלי זיהוי מהטלפון הזה",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/** Options for a car that's already shared. */
@Composable
fun SharedVehicleOptionsDialog(
    vehicle: Vehicle,
    onInvite: () -> Unit,
    onStopSharing: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\"${vehicle.name}\" משותף") },
        text = {
            Text(
                if (vehicle.isViewOnly) {
                    "אתה רואה את מיקום החנייה של הרכב הזה בזמן אמת. הפסקת השיתוף תסיר אותו מהטלפון."
                } else {
                    "כל מי שמשותף ברכב רואה כאן בזמן אמת איפה הוא חנה. הפסקת השיתוף משאירה את הרכב וההיסטוריה שלו אצלך."
                }
            )
        },
        confirmButton = { TextButton(onClick = onInvite) { Text("הזמן שותף") } },
        dismissButton = {
            TextButton(onClick = onStopSharing) {
                Text("הפסק שיתוף", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}
