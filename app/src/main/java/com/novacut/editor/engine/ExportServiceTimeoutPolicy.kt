package com.novacut.editor.engine

import android.content.pm.ServiceInfo

internal fun shouldFailExportForForegroundServiceTimeout(foregroundServiceType: Int?): Boolean {
    return foregroundServiceType == null ||
        foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING != 0
}
