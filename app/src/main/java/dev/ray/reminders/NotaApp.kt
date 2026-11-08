package dev.ray.reminders

import android.app.Application
import android.content.Context
import java.io.File

/** Saves the details of any crash so Nota can offer to copy them on the next launch. */
class NotaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val info = packageManager.getPackageInfo(packageName, 0)
                CrashLog.file(this).writeText(
                    "Nota ${info.versionName} (${info.longVersionCode}) · Android ${android.os.Build.VERSION.RELEASE} · " +
                        "${android.os.Build.MODEL}\n${java.time.LocalDateTime.now()}\n\n" + error.stackTraceToString().take(12_000),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}

object CrashLog {
    fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")
    fun read(ctx: Context): String? = file(ctx).takeIf { it.exists() }?.readText()
    fun clear(ctx: Context) { file(ctx).delete() }
}
