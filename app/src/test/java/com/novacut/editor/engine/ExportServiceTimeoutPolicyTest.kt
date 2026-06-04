package com.novacut.editor.engine

import android.content.pm.ServiceInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportServiceTimeoutPolicyTest {

    @Test
    fun `legacy timeout callback fails active export`() {
        assertTrue(shouldFailExportForForegroundServiceTimeout(null))
    }

    @Test
    fun `media processing timeout type fails active export`() {
        assertTrue(
            shouldFailExportForForegroundServiceTimeout(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            )
        )
    }

    @Test
    fun `combined service types fail when media processing bit is present`() {
        val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING or
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC

        assertTrue(shouldFailExportForForegroundServiceTimeout(serviceType))
    }

    @Test
    fun `unrelated timeout type stops service without changing export state`() {
        assertFalse(
            shouldFailExportForForegroundServiceTimeout(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        )
    }
}
