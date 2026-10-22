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
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import java.time.ZoneId
import net.jkraemer.photos.core.DateLabels
import net.jkraemer.photos.core.MediaAccess
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.media.MediaStoreRepository
import net.jkraemer.photos.media.MediaThumbnailFetcher
import net.jkraemer.photos.media.MediaThumbnailKeyer
import net.jkraemer.photos.media.shareIntent
import net.jkraemer.photos.media.trashRequest
import net.jkraemer.photos.ui.PermissionGate
import net.jkraemer.photos.ui.detail.DetailActions
import net.jkraemer.photos.ui.detail.DetailScreen
import net.jkraemer.photos.ui.grid.GridScreen

/** Composition root: wires the MediaStore repository and the permission gate. */
class MainActivity : ComponentActivity() {
    private var access by mutableStateOf(MediaAccess.NONE)
    private val requestAccess = registerForActivityResult(RequestMultiplePermissions()) { refreshAccess() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshAccess()
        setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context)
                    .components {
                        add(MediaThumbnailFetcher.Factory())
                        add(MediaThumbnailKeyer())
                    }
                    .build()
            }
            MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color.Black)) {
                Surface(Modifier.fillMaxSize()) {
                    PermissionGate(
                        access,
                        onRequest = { requestAccess.launch(MEDIA_PERMISSIONS) },
                        onOpenSettings = ::openAppSettings,
                    ) {
                        // Created only once access is granted, so the first
                        // query already sees the whole camera roll.
                        val viewModel: PhotosViewModel = viewModel(
                            factory = viewModelFactory { initializer { PhotosViewModel(MediaStoreRepository(contentResolver)) } },
                        )
                        PhotosScreen(viewModel)
                    }
                }
            }
        }
    }

    @Composable
    private fun PhotosScreen(viewModel: PhotosViewModel) {
        val items = viewModel.timeline.collectAsStateWithLifecycle().value ?: return
        val level by viewModel.zoomLevel.collectAsStateWithLifecycle()
        val anchor by viewModel.gridAnchor.collectAsStateWithLifecycle()
        val openItemId by viewModel.openItemId.collectAsStateWithLifecycle()
        val locale = LocalConfiguration.current.locales[0]
        val labels = remember(locale) { DateLabels(locale, ZoneId.systemDefault()) }
        // The detail view overlays the grid, so the grid keeps its scroll
        // state underneath and only needs the last viewed item as its anchor.
        Box(Modifier.fillMaxSize()) {
            GridScreen(
                items,
                level,
                labels,
                anchor,
                onAnchorChange = viewModel::anchorGrid,
                onLevelChange = viewModel::setZoomLevel,
                onOpen = viewModel::open,
            )
            openItemId?.let { id ->
                DetailScreen(items, id, labels, onClose = viewModel::close, actions = detailActions)
            }
        }
    }

    // A confirmed trash needs no handling here: the MediaStore observer emits
    // the new timeline and the detail view moves on to the neighbour.
    private val confirmTrash = registerForActivityResult(StartIntentSenderForResult()) {}

    private val detailActions = object : DetailActions {
        override fun share(item: MediaItem) {
            startActivity(shareIntent(item))
        }

        override fun trash(item: MediaItem) {
            confirmTrash.launch(IntentSenderRequest.Builder(trashRequest(contentResolver, item)).build())
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
