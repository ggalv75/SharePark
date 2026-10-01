package com.sharepark.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sharepark.data.remote.cloud.CloudUser
import com.sharepark.ui.components.AppCard
import com.sharepark.ui.components.PrimaryPillButton
import com.sharepark.ui.components.SecondaryPillButton

/** Signed-in state for shared vehicles. Only shown when the build has a Firebase project. */
@Composable
fun AccountCard(
    user: CloudUser?,
    error: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Initials avatar on a soft gray disc; a generic person icon when signed out
            Box(
                modifier = Modifier
                    .padding(end = 14.dp)
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val initials = user?.displayName
                    ?.split(" ")
                    ?.filter { it.isNotBlank() }
                    ?.take(2)
                    ?.joinToString("") { it.first().uppercase() }
                if (!initials.isNullOrEmpty()) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user?.displayName ?: "חשבון לרכבים משותפים",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = user?.email ?: "התחבר כדי לשתף רכב עם בני משפחה ולראות בזמן אמת איפה הוא חונה",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (user == null) {
            PrimaryPillButton(text = "התחבר עם Google", onClick = onSignIn, modifier = Modifier.fillMaxWidth())
        } else {
            SecondaryPillButton(text = "התנתק", onClick = onSignOut, modifier = Modifier.fillMaxWidth())
        }
    }
}
