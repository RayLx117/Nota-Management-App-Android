package dev.ray.reminders.alarm

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Stopwatch: [base] is time banked before the current run; [startedAt] > 0 while running. Laps are totals. */
data class StopwatchState(val base: Long = 0, val startedAt: Long = 0, val laps: List<Long> = emptyList()) {
    val running get() = startedAt > 0
    fun elapsed(now: Long) = base + if (running) now - startedAt else 0
}

object Stopwatch {
    private var flow: MutableStateFlow<StopwatchState>? = null
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("stopwatch", Context.MODE_PRIVATE)

    fun state(ctx: Context): StateFlow<StopwatchState> = flow ?: MutableStateFlow(load(ctx)).also { flow = it }

    private fun load(ctx: Context) = prefs(ctx).let { p ->
        StopwatchState(
            p.getLong("base", 0), p.getLong("startedAt", 0),
            p.getString("laps", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() },
        )
    }

    private fun set(ctx: Context, s: StopwatchState) {
        prefs(ctx).edit().putLong("base", s.base).putLong("startedAt", s.startedAt)
            .putString("laps", s.laps.joinToString(",")).apply()
        (state(ctx) as MutableStateFlow).value = s
    }

    private fun now() = System.currentTimeMillis()

    fun start(ctx: Context) { val s = state(ctx).value; if (!s.running) set(ctx, s.copy(startedAt = now())) }
    fun stop(ctx: Context) { val s = state(ctx).value; if (s.running) set(ctx, s.copy(base = s.elapsed(now()), startedAt = 0)) }
    fun lap(ctx: Context) { val s = state(ctx).value; if (s.running) set(ctx, s.copy(laps = s.laps + s.elapsed(now()))) }
    fun reset(ctx: Context) = set(ctx, StopwatchState())

    /** 1:02:03.45 / 02:03.45 */
    fun format(ms: Long): String {
        val cs = ms / 10 % 100; val total = ms / 1000
        val h = total / 3600; val m = total % 3600 / 60; val s = total % 60
        return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, cs) else "%02d:%02d.%02d".format(m, s, cs)
    }
}
