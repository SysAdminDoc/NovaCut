package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub engine -- cross-device project sync. See ROADMAP.md Tier C.16.
 *
 * Extends [ProjectArchive] (already handles ZIP export) with rsync-like delta
 * transfer and conflict resolution. Backend options the UI exposes:
 *
 *  - LOCAL_FOLDER  -- Syncthing-style: user picks a folder, engine writes/reads
 *                     there. Syncthing / Google Drive / OneDrive clients do the
 *                     actual replication.
 *  - SELF_HOSTED   -- User-provided HTTP endpoint (Nextcloud WebDAV, Git LFS, etc.)
 *  - LAN_PEER      -- Direct peer sync between two devices on the same LAN via
 *                     discoverable mDNS service.
 *
 * Conflict policy: last-writer-wins is forbidden (would silently clobber work).
 * Engine instead surfaces a merge dialog when remote has diverged since local
 * last-synced hash, offering: keep local, keep remote, or open both.
 */
@Singleton
class ProjectSyncEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    enum class Backend { LOCAL_FOLDER, SELF_HOSTED, LAN_PEER }

    data class SyncTarget(
        val backend: Backend,
        val endpoint: String,
        val deviceName: String,
        val lastSyncAtMs: Long? = null
    )

    data class SyncPlan(
        val projectId: String,
        val target: SyncTarget,
        val localRevision: String,
        val remoteRevision: String?,
        val changedFiles: List<String>,
        val conflict: Conflict?
    ) {
        enum class Conflict { NONE, LOCAL_AHEAD, REMOTE_AHEAD, DIVERGED }
    }

    data class SyncResult(
        val bytesUploaded: Long,
        val bytesDownloaded: Long,
        val conflictResolved: SyncPlan.Conflict,
        val errors: List<String>
    )

    private val _configuredTargets = MutableStateFlow<List<SyncTarget>>(emptyList())
    val configuredTargets: StateFlow<List<SyncTarget>> = _configuredTargets

    // CAS-loop mutation so two concurrent add/remove calls can't lose updates via
    // read-modify-write race (which a plain `value = value + target` assignment would).
    fun addTarget(target: SyncTarget) {
        while (true) {
            val cur = _configuredTargets.value
            if (target in cur) return
            if (_configuredTargets.compareAndSet(cur, cur + target)) return
        }
    }

    fun removeTarget(target: SyncTarget) {
        while (true) {
            val cur = _configuredTargets.value
            if (target !in cur) return
            if (_configuredTargets.compareAndSet(cur, cur - target)) return
        }
    }

    /**
     * Inspect the sync state between local and remote without modifying either.
     * Returns null when the target is unreachable.
     */
    suspend fun plan(projectId: String, target: SyncTarget): SyncPlan? = withContext(Dispatchers.IO) {
        Log.d(TAG, "plan: stub -- sync backend not wired ($projectId -> ${target.backend})")
        null
    }

    suspend fun sync(
        plan: SyncPlan,
        resolution: ConflictResolution = ConflictResolution.ABORT_ON_CONFLICT,
        onProgress: (Float) -> Unit = {}
    ): SyncResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "sync: stub -- backend not wired (${plan.projectId})")
        SyncResult(0L, 0L, SyncPlan.Conflict.NONE, listOf("Sync backend not implemented"))
    }

    enum class ConflictResolution {
        ABORT_ON_CONFLICT,
        KEEP_LOCAL,
        KEEP_REMOTE,
        SAVE_BOTH_AS_COPIES
    }

    companion object {
        private const val TAG = "ProjectSync"
    }
}
