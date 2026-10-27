package net.jkraemer.photos

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * ML Kit uploads usage events through Google DataTransport. The app's
 * manifest strips the upload path; these pin each removal, so an ML Kit
 * upgrade that brings it back fails here.
 */
@RunWith(RobolectricTestRunner::class)
class TelemetryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val pm = context.packageManager
    private fun component(name: String) = ComponentName(context.packageName, "com.google.android.datatransport.runtime.$name")

    @Test fun `no upload backend is registered`() {
        val discovery = pm.getServiceInfo(component("backends.TransportBackendDiscovery"), PackageManager.GET_META_DATA)
        assertNull(discovery.metaData?.keySet()?.firstOrNull { it.startsWith("backend:") })
    }

    @Test fun `no upload job service`() =
        assertFalse(declaresService("scheduling.jobscheduling.JobInfoSchedulerService"))

    @Test fun `no upload alarm receiver`() {
        val receivers = pm.getPackageInfo(context.packageName, PackageManager.GET_RECEIVERS).receivers.orEmpty()
        assertTrue(receivers.none { it.name.endsWith("AlarmManagerSchedulerBroadcastReceiver") })
    }

    @Test fun `none of DataTransport's permissions`() {
        val permissions = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(Manifest.permission.ACCESS_NETWORK_STATE in permissions)
        assertFalse(Manifest.permission.WAKE_LOCK in permissions)
    }

    private fun declaresService(name: String) =
        pm.getPackageInfo(context.packageName, PackageManager.GET_SERVICES).services.orEmpty()
            .any { it.name == "com.google.android.datatransport.runtime.$name" }
}
