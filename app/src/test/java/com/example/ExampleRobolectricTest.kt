package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches FolioScan`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("FolioScan", appName)
    }

    @Test
    fun `merged manifest strictly contains zero INTERNET permission`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = info.requestedPermissions?.toSet().orEmpty()
        assertFalse("INTERNET permission must never be present", Manifest.permission.INTERNET in permissions)
        assertFalse("ACCESS_NETWORK_STATE must never be present", Manifest.permission.ACCESS_NETWORK_STATE in permissions)
    }
}
