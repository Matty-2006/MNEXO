package ec.mnexo.platform

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberMicrophonePermission(): MicrophonePermission {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending[0]?.invoke(granted)
        pending[0] = null
    }
    return remember(context, launcher) {
        object : MicrophonePermission {
            override val isGranted: Boolean
                get() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

            override fun request(onResult: (granted: Boolean) -> Unit) {
                if (isGranted) {
                    onResult(true)
                    return
                }
                pending[0] = onResult
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }

            override fun openAppSettings() {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }
}
