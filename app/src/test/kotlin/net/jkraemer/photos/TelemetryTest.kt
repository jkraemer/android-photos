package net.jkraemer.photos

import android.content.ComponentName
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * ML Kit uploads usage events through Google DataTransport. The app's
 * manifest strips the upload path; these pin it, so an ML Kit upgrade that
 * brings it back, or adds any other background component, fails here and
 * gets reviewed.
 */
@RunWith(RobolectricTestRunner::class)
class TelemetryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val pm = context.packageManager

    @Test fun `declares only the reviewed services, receivers and providers`() {
        val flags = PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
        val info = pm.getPackageInfo(context.packageName, flags)
        val declared = listOf(info.services, info.receivers, info.providers).flatMap { it.orEmpty().map { c -> c.name } }.toSet()
        assertEquals(
            setOf(
                // Lists ML Kit's components; harmless without an upload backend.
                "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery",
                "com.google.mlkit.common.internal.MlKitComponentDiscoveryService",
                "com.google.mlkit.common.internal.MlKitInitProvider",
                "androidx.startup.InitializationProvider",
                "androidx.profileinstaller.ProfileInstallReceiver",
            ),
            declared,
        )
    }

    @Test fun `no upload backend is registered`() {
        val discovery = ComponentName(context.packageName, "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery")
        val metaData = pm.getServiceInfo(discovery, PackageManager.GET_META_DATA).metaData
        assertNull(metaData?.keySet()?.firstOrNull { it.startsWith("backend:") })
    }
}
