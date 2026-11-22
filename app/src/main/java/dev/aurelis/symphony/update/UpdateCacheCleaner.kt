package dev.aurelis.symphony.update

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Only touches APKs / partial downloads created by AurelisUpdater inside
 * cacheDir/updates. Never clears Coil, Media3, library, playlists, or account data.
 */
internal object UpdateCacheCleaner {
    private const val JOB_ID = 0x415552
    private val retention = TimeUnit.HOURS.toMillis(24)
    private val partialRetention = TimeUnit.HOURS.toMillis(2)
    private val maximumAgeGrace = TimeUnit.HOURS.toMillis(36)
    private val numericVersion = Regex("""(\d+)\.(\d+)\.(\d+)""")

    private fun updateDir(context: Context) = File(context.cacheDir, "updates")
    private fun validFile(file: File) = file.isFile && (
        file.name == "pending.apk" ||
            (file.name.startsWith("Aurelis-") &&
                (file.name.endsWith(".apk") || file.name.endsWith(".part")))
    )

    private fun parts(name: String): List<Int>? {
        val m = numericVersion.find(name) ?: return null
        return m.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
    }

    @Suppress("DEPRECATION")
    private fun installedVersion(context: Context): List<Int>? = runCatching {
        val name = context.packageManager.getPackageInfo(context.packageName, 0)
            .versionName.orEmpty()
        parts(name)
    }.getOrNull()

    private fun isAlreadyInstalled(name: String, installed: List<Int>?): Boolean {
        val archived = parts(name) ?: return false
        val current = installed ?: return false
        for (i in 0..2) {
            if (archived[i] < current[i]) return true
            if (archived[i] > current[i]) return false
        }
        return true
    }

    /**
     * Safe to call after app upgrades, at startup, after downloads, and from
     * the scheduled maintenance job. Only one still-relevant APK is retained.
     */
    @Synchronized
    fun prune(context: Context, keep: File? = null, forceAll: Boolean = false): Long {
        val dir = updateDir(context)
        if (!dir.isDirectory) return 0
        val installed = installedVersion(context)
        val now = System.currentTimeMillis()
        val protectedPath = keep?.canonicalPath
        var freed = 0L
        val apks = mutableListOf<File>()
        for (file in dir.listFiles().orEmpty()) {
            if (!validFile(file)) continue
            if (file.canonicalPath == protectedPath) continue
            val age = if (file.lastModified() > 0)
                (now - file.lastModified()).coerceAtLeast(0L) else maximumAgeGrace
            val partial = file.name == "pending.apk" || file.name.endsWith(".part")
            val delete = forceAll ||
                (partial && age >= partialRetention) ||
                (!partial && (age >= retention ||
                    isAlreadyInstalled(file.name, installed)))
            if (delete) {
                val size = file.length()
                if (file.delete()) freed += size
            } else if (!partial) {
                apks.add(file)
            }
        }
        // No accumulation when several different releases are downloaded in a day.
        if (!forceAll) {
            val extra = apks.sortedByDescending { it.lastModified() }.drop(1)
            for (file in extra) {
                val size = file.length()
                if (file.delete()) freed += size
            }
        }
        return freed
    }

    @Synchronized
    fun bytes(context: Context): Long = updateDir(context)
        .listFiles().orEmpty().filter(::validFile).sumOf { it.length() }

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE)
            as? JobScheduler ?: return
        val job = JobInfo.Builder(JOB_ID,
            ComponentName(context, UpdateCacheJobService::class.java))
            .setMinimumLatency(retention)
            .setOverrideDeadline(maximumAgeGrace)
            .build()
        scheduler.schedule(job)
    }
}

/** Android manages this one-shot cache cleanup even if the UI is closed. */
class UpdateCacheJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread({
            try {
                UpdateCacheCleaner.prune(applicationContext)
            } finally {
                jobFinished(params, false)
            }
        }, "AurelisUpdateCacheCleanup").start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}
