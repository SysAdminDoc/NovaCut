package com.novacut.editor.ui.editor

import com.novacut.editor.engine.MediaHealthIssue
import com.novacut.editor.engine.MediaHealthIssueType
import com.novacut.editor.engine.MediaHealthReport
import com.novacut.editor.engine.MediaHealthSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecoveryMediaStatusTest {

    @Test
    fun nullReportHasNoStatus() {
        assertNull(recoveryMediaStatusFor(null))
    }

    @Test
    fun emptyRecoveryShowsNoMedia() {
        val status = recoveryMediaStatusFor(report(totalReferences = 0))

        assertEquals(RecoveryMediaStatusKind.NO_MEDIA, status?.kind)
        assertEquals(0, status?.totalReferences)
    }

    @Test
    fun cleanReferencesShowReady() {
        val status = recoveryMediaStatusFor(report(totalReferences = 3))

        assertEquals(RecoveryMediaStatusKind.READY, status?.kind)
        assertEquals(3, status?.totalReferences)
        assertEquals(0, status?.blockingCount)
        assertEquals(0, status?.warningCount)
    }

    @Test
    fun blockingIssueNeedsRepair() {
        val status = recoveryMediaStatusFor(
            report(
                MediaHealthIssue(
                    type = MediaHealthIssueType.MISSING_LOCAL_FILE,
                    severity = MediaHealthSeverity.BLOCKING,
                    subjectId = "clip-1",
                    message = "missing"
                )
            )
        )

        assertEquals(RecoveryMediaStatusKind.NEEDS_REPAIR, status?.kind)
        assertEquals(1, status?.blockingCount)
    }

    @Test
    fun missingProxyUsesFallbackStatus() {
        val status = recoveryMediaStatusFor(
            report(
                MediaHealthIssue(
                    type = MediaHealthIssueType.MISSING_PROXY_FILE,
                    severity = MediaHealthSeverity.WARNING,
                    subjectId = "clip-1",
                    message = "proxy missing"
                )
            )
        )

        assertEquals(RecoveryMediaStatusKind.PROXY_FALLBACK, status?.kind)
        assertEquals(1, status?.warningCount)
    }

    @Test
    fun genericWarningsStayWarnings() {
        val status = recoveryMediaStatusFor(
            report(
                MediaHealthIssue(
                    type = MediaHealthIssueType.EXTERNAL_SOURCE,
                    severity = MediaHealthSeverity.WARNING,
                    subjectId = "clip-1",
                    message = "external"
                )
            )
        )

        assertEquals(RecoveryMediaStatusKind.WARNINGS, status?.kind)
        assertEquals(1, status?.warningCount)
    }

    private fun report(
        vararg issues: MediaHealthIssue,
        totalReferences: Int = 1
    ): MediaHealthReport {
        return MediaHealthReport(
            totalReferences = totalReferences,
            managedAssets = totalReferences,
            localReadyReferences = totalReferences,
            externalReferences = 0,
            issues = issues.toList()
        )
    }
}
