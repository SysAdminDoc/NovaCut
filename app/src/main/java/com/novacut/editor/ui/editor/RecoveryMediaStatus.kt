package com.novacut.editor.ui.editor

import com.novacut.editor.engine.MediaHealthIssueType
import com.novacut.editor.engine.MediaHealthReport

enum class RecoveryMediaStatusKind {
    NO_MEDIA,
    READY,
    NEEDS_REPAIR,
    PROXY_FALLBACK,
    WARNINGS
}

data class RecoveryMediaStatus(
    val kind: RecoveryMediaStatusKind,
    val totalReferences: Int,
    val blockingCount: Int,
    val warningCount: Int
)

fun recoveryMediaStatusFor(report: MediaHealthReport?): RecoveryMediaStatus? {
    if (report == null) return null
    if (report.totalReferences == 0) {
        return RecoveryMediaStatus(
            kind = RecoveryMediaStatusKind.NO_MEDIA,
            totalReferences = 0,
            blockingCount = 0,
            warningCount = report.warningCount
        )
    }
    if (report.blockingCount > 0) {
        return RecoveryMediaStatus(
            kind = RecoveryMediaStatusKind.NEEDS_REPAIR,
            totalReferences = report.totalReferences,
            blockingCount = report.blockingCount,
            warningCount = report.warningCount
        )
    }
    val proxyFallback = report.issues.any {
        it.type == MediaHealthIssueType.MISSING_PROXY_FILE ||
            it.type == MediaHealthIssueType.EMPTY_PROXY_FILE
    }
    if (proxyFallback) {
        return RecoveryMediaStatus(
            kind = RecoveryMediaStatusKind.PROXY_FALLBACK,
            totalReferences = report.totalReferences,
            blockingCount = 0,
            warningCount = report.warningCount
        )
    }
    if (report.warningCount > 0) {
        return RecoveryMediaStatus(
            kind = RecoveryMediaStatusKind.WARNINGS,
            totalReferences = report.totalReferences,
            blockingCount = 0,
            warningCount = report.warningCount
        )
    }
    return RecoveryMediaStatus(
        kind = RecoveryMediaStatusKind.READY,
        totalReferences = report.totalReferences,
        blockingCount = 0,
        warningCount = 0
    )
}
