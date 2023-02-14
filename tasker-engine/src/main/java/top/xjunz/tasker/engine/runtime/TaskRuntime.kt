/*
 * Copyright (c) 2022 xjunz. All rights reserved.
 */

package top.xjunz.tasker.engine.runtime

import android.util.ArrayMap
import androidx.core.util.Pools.SynchronizedPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import top.xjunz.shared.ktx.casted
import top.xjunz.tasker.engine.applet.base.Applet
import top.xjunz.tasker.engine.applet.base.AppletResult
import top.xjunz.tasker.engine.applet.base.Flow
import top.xjunz.tasker.engine.applet.base.WaitFor
import top.xjunz.tasker.engine.task.XTask
import java.util.zip.CRC32

/**
 * The structure storing runtime information of a running [XTask].
 *
 * @author xjunz 2022/08/09
 */
class TaskRuntime private constructor() {

    private object Pool : SynchronizedPool<TaskRuntime>(10)

    private class IndexedReferent(val which: Int, private val referent: Any?) {

        fun getReferentValue(): Any? {
            return if (referent is Referent) referent.getReferredValue(which) else referent
        }
    }

    interface Observer {

        fun onAppletStarted(victim: Applet, runtime: TaskRuntime) {}

        fun onAppletTerminated(victim: Applet, runtime: TaskRuntime) {}

        fun onAppletSkipped(victim: Applet, runtime: TaskRuntime) {}
    }

    companion object {

        fun XTask.obtainRuntime(
            eventScope: EventScope,
            coroutineScope: CoroutineScope,
            events: Array<out Event>
        ): TaskRuntime {
            val instance = Pool.acquire() ?: TaskRuntime()
            instance.task = this
            instance.runtimeScope = coroutineScope
            instance.target = events
            instance._events = events
            instance._eventScope = eventScope
            return instance
        }

        fun drainPool() {
            while (Pool.acquire() != null) {
                /* no-op */
            }
        }
    }

    val tracker = AppletIndexer()

    val isActive get() = runtimeScope?.isActive == true

    val result: AppletResult get() = _result!!

    val events: Array<out Event> get() = _events!!

    /**
     * Identify all arguments applied to the task runtime.
     */
    val fingerprint: Long get() = crc.value

    var isSuspending = false

    var observer: Observer? = null

    /**
     * Whether the applying of current applet is successful.
     */
    var isSuccessful = true

    var waitingFor: WaitFor? = null

    lateinit var hitEvent: Event

    lateinit var task: XTask

    lateinit var currentApplet: Applet

    lateinit var currentFlow: Flow

    private val eventScope: EventScope get() = _eventScope!!

    private var _events: Array<out Event>? = null

    private var _result: AppletResult? = null

    private var _eventScope: EventScope? = null

    @get:Synchronized
    @set:Synchronized
    private var runtimeScope: CoroutineScope? = null

    /**
     * Target is for applets in a specific flow to use in runtime.
     */
    private var target: Any? = null

    private val crc = CRC32()

    private val referents = ArrayMap<String, IndexedReferent>()

    fun updateFingerprint(any: Any?) {
        crc.update(any.hashCode())
    }

    fun ensureActive() {
        runtimeScope?.ensureActive()
    }

    fun halt() {
        runtimeScope?.cancel()
    }

    /**
     * Get or put a global variable if absent. The variable can be shared across tasks. More specific,
     * within an [EventScope].
     */
    fun <V : Any> getGlobal(key: Long, initializer: (() -> V)? = null): V {
        if (initializer == null) {
            return eventScope.registry.getValue(key).casted()
        }
        return eventScope.registry.getOrPut(key, initializer).casted()
    }

    fun getReferentOf(applet: Applet, which: Int): Any? {
        val name = applet.references[which]
        if (name != null) {
            return getReferentByName(name)
        }
        return null
    }

    fun getAllReferentOf(applet: Applet): Array<Any?> {
        return if (applet.references.isEmpty()) {
            emptyArray()
        } else {
            Array(applet.references.size) {
                getReferentByName(applet.references[it])
            }
        }
    }

    fun registerReferent(applet: Applet, referent: Any?) {
        applet.referents.forEach { (which, name) ->
            referents[name] = IndexedReferent(which, referent)
        }
    }

    fun registerResult(applet: Applet, result: AppletResult) {
        _result = result
        if (result.isSuccessful && result.returned != null) {
            registerReferent(applet, result.returned!!)
        }
    }

    private fun getReferentByName(name: String?): Any? {
        return referents[name]?.getReferentValue()
    }

    fun setTarget(any: Any?) {
        target = any
    }

    fun getRawTarget(): Any? {
        return target
    }

    fun <T> getTarget(): T {
        return requireNotNull(target) {
            "Target is not set!"
        }.casted()
    }

    fun triggerWaitFor(events: Array<out Event>) {
        waitingFor?.let {
            _events = events
            it.trigger()
        }
    }

    fun recycle() {
        _eventScope = null
        _events = null
        runtimeScope = null
        referents.clear()
        tracker.reset()
        isSuspending = false
        observer = null
        _result = null
        target = null
        isSuccessful = true
        waitingFor = null
        crc.reset()
        Pool.release(this)
    }

    override fun toString(): String {
        return "TaskRuntime@${hashCode().toString(16)}(${task.title})"
    }
}