package net.jkraemer.photos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import net.jkraemer.photos.core.MediaAccess
import net.jkraemer.photos.media.MediaStoreRepository
import net.jkraemer.photos.ui.PermissionGate

/** Composition root: wires the MediaStore repository and the permission gate. */
class MainActivity : ComponentActivity() {
    private var access by mutableStateOf(MediaAccess.NONE)
    private val requestAccess = registerForActivityResult(RequestMultiplePermissions()) { refreshAccess() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshAccess()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color.Black)) {
                Surface(Modifier.fillMaxSize()) {
                    PermissionGate(
                        access,
                        onRequest = { requestAccess.launch(MEDIA_PERMISSIONS) },
                        onOpenSettings = ::openAppSettings,
                    ) {
                        // Created only once access is granted, so the first
                        // query already sees the whole camera roll.
                        viewModel<PhotosViewModel>(
                            factory = viewModelFactory { initializer { PhotosViewModel(MediaStoreRepository(contentResolver)) } },
                        )
                        Box(Modifier.fillMaxSize().background(Color.Black))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have changed access in the settings app meanwhile.
        refreshAccess()
    }

    private fun refreshAccess() {
        fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        access = MediaAccess.of(
            images = granted(Manifest.permission.READ_MEDIA_IMAGES),
            videos = granted(Manifest.permission.READ_MEDIA_VIDEO),
            userSelected = granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
        )
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    private companion object {
        val MEDIA_PERMISSIONS = arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
    }
}
