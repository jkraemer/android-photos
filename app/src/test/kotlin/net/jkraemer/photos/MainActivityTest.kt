package net.jkraemer.photos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    @Test fun `shows no title bar`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertNull(it.actionBar) }
        }
    }

    @Test fun `declares the media location permission`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertTrue(Manifest.permission.ACCESS_MEDIA_LOCATION in info.requestedPermissions.orEmpty())
    }

    @Test fun `declares the internet permission the system geocoder demands`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertTrue(Manifest.permission.INTERNET in info.requestedPermissions.orEmpty())
    }
}
