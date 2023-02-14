/*
 * Copyright (c) 2022 xjunz. All rights reserved.
 */

package top.xjunz.tasker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.IAccessibilityServiceClient
import android.app.IUiAutomationConnection
import android.app.UiAutomation
import android.app.UiAutomationHidden
import android.app.accessibilityservice.AccessibilityServiceHidden
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.InputEvent
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import androidx.test.uiautomator.bridge.UiAutomatorBridge
import top.xjunz.shared.ktx.casted
import top.xjunz.shared.trace.logcatStackTrace
import top.xjunz.shared.utils.unsupportedOperation
import top.xjunz.tasker.bridge.OverlayToastBridge
import top.xjunz.tasker.engine.runtime.Event
import top.xjunz.tasker.engine.task.EventDispatcher
import top.xjunz.tasker.ktx.isTrue
import top.xjunz.tasker.task.applet.flow.ref.ComponentInfoWrapper
import top.xjunz.tasker.task.event.A11yEventDispatcher
import top.xjunz.tasker.task.inspector.FloatingInspector
import top.xjunz.tasker.task.inspector.InspectorMode
import top.xjunz.tasker.task.inspector.InspectorViewModel
import top.xjunz.tasker.task.runtime.LocalTaskManager
import top.xjunz.tasker.task.runtime.ResidentTaskScheduler
import top.xjunz.tasker.uiautomator.A11yUiAutomatorBridge
import top.xjunz.tasker.util.ReflectionUtil.isLazilyInitialized
import top.xjunz.tasker.util.ReflectionUtil.requireFieldFromSuperClass
import java.lang.ref.WeakReference

/**
 * @see ShizukuAutomatorService
 * @author xjunz 2022/07/12
 */
class A11yAutomatorService : AccessibilityService(), AutomatorService, IUiAutomationConnection,
    LifecycleOwner {

    companion object {

        /**
         * Indicate that the service is served as [FloatingInspector].
         */
        var FLAG_REQUEST_INSPECTOR_MODE: Boolean = false

        val launchError = MutableLiveData<Throwable>()

        val runningState = MutableLiveData<Boolean>()

        private var instance: WeakReference<A11yAutomatorService>? = null

        fun get() = instance?.get()

        fun require() = checkNotNull(get()) {
            "The A11yAutomatorService is not yet started or is dead!"
        }
    }

    var isInspectorMode = false
        private set

    lateinit var inspector: FloatingInspector
        private set

    private val lifecycleRegistry = LifecycleRegistry(this)

    private val uiAutomation: UiAutomation get() = uiAutomationHidden.casted()

    private var startTimestamp: Long = -1

    private var callbacks: AccessibilityServiceHidden.Callbacks? = null

    private lateinit var uiAutomationHidden: UiAutomationHidden

    private lateinit var inspectorViewModel: InspectorViewModel

    override val residentTaskScheduler: ResidentTaskScheduler by lazy {
        ResidentTaskScheduler(LocalTaskManager)
    }

    override val isRunning get() = runningState.isTrue

    override val uiAutomatorBridge: UiAutomatorBridge by lazy {
        A11yUiAutomatorBridge(uiAutomation)
    }

    override val a11yEventDispatcher: A11yEventDispatcher by lazy {
        A11yEventDispatcher(Looper.getMainLooper(), uiAutomatorBridge)
    }

    override val overlayToastBridge: OverlayToastBridge by lazy {
        OverlayToastBridge(Looper.getMainLooper())
    }

    private val componentChangeCallbackForInspector by lazy {
        EventDispatcher.Callback {
            val hit = it.find { event ->
                event.type == Event.EVENT_ON_CONTENT_CHANGED
                        || event.type == Event.EVENT_ON_PACKAGE_ENTERED
            }
            if (hit != null)
                inspectorViewModel.currentComp.postValue(
                    ComponentInfoWrapper.wrap(hit.componentInfo)
                )
        }
    }

    fun startListeningComponentChanges() {
        a11yEventDispatcher.addCallback(componentChangeCallbackForInspector)
    }

    private fun stopListeningComponentChanges() {
        a11yEventDispatcher.removeCallback(componentChangeCallbackForInspector)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            isInspectorMode = FLAG_REQUEST_INSPECTOR_MODE
            instance = WeakReference(this)
            uiAutomationHidden = UiAutomationHidden(mainLooper, this)
            uiAutomationHidden.connect()
            if (!isInspectorMode) {
                residentTaskScheduler.scheduleTasks(a11yEventDispatcher)
            }
            a11yEventDispatcher.startProcessing()
            runningState.value = true
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            startTimestamp = System.currentTimeMillis()
        } catch (t: Throwable) {
            t.logcatStackTrace()
            launchError.value = t
            destroy()
        } finally {
            FLAG_REQUEST_INSPECTOR_MODE = false
        }
    }

    fun suppressTaskScheduler() {
        if (::residentTaskScheduler.isLazilyInitialized) {
            residentTaskScheduler.isSuppressed = true
        }
    }

    fun destroyFloatingInspector() {
        inspector.dismiss()
        stopListeningComponentChanges()
        if (isInspectorMode) {
            disableSelf()
        } else if (::residentTaskScheduler.isLazilyInitialized) {
            residentTaskScheduler.isSuppressed = false
        }
    }

    fun isInspectorShown(): Boolean {
        return ::inspector.isInitialized && inspector.isShown
    }

    fun showFloatingInspector(mode: InspectorMode) {
        if (isInspectorShown()) return
        inspectorViewModel = InspectorViewModel()
        inspectorViewModel.currentMode.value = mode
        inspector = FloatingInspector(this, inspectorViewModel)
        inspector.show()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isInspectorShown()) {
            // Recreate the inspector
            inspector.dismiss()
            inspectorViewModel.onConfigurationChanged()
            inspector = FloatingInspector(this, inspectorViewModel)
            inspector.show()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        callbacks?.onAccessibilityEvent(event)
    }

    override fun onInterrupt() {

    }

    override fun onDestroy() {
        super.onDestroy()
        a11yEventDispatcher.destroy()
        if (::overlayToastBridge.isLazilyInitialized) {
            overlayToastBridge.destroy()
        }
        if (::residentTaskScheduler.isLazilyInitialized) {
            residentTaskScheduler.shutdown()
        }
        LocalTaskManager.clearAllSnapshots()
        if (isInspectorShown()) inspector.dismiss()
        if (::uiAutomationHidden.isInitialized) {
            uiAutomationHidden.disconnect()
        }
        instance?.clear()
        runningState.value = false
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    /**
     * Connect to the [UiAutomation] with limited features in a delicate way. Should take this
     * really carefully. After connected, we can use [UiAutomation.setOnAccessibilityEventListener],
     * [UiAutomation.waitForIdle] and [UiAutomation.executeAndWaitForEvent].
     *
     * This method will be called on [UiAutomationHidden.connect].
     */
    override fun connect(client: IAccessibilityServiceClient, flags: Int) {
        val windowToken: IBinder = requireFieldFromSuperClass("mWindowToken")
        val connectionId: Int = requireFieldFromSuperClass("mConnectionId")
        callbacks = client.requireFieldFromSuperClass("mCallback")
        requireNotNull(callbacks).init(connectionId, windowToken)
    }

    override fun disconnect() {
        destroy()
    }

    override fun destroy() {
        disableSelf()
    }

    override fun getStartTimestamp(): Long {
        return startTimestamp
    }

    override fun createAvailabilityChecker(): IAvailabilityChecker {
        return AvailabilityChecker(this)
    }

    override fun setRotation(rotation: Int): Boolean {
        unsupportedOperation()
    }

    override fun takeScreenshot(crop: Rect, rotation: Int): Bitmap {
        unsupportedOperation()
    }

    override fun executeShellCommand(
        command: String?, sink: ParcelFileDescriptor?, source: ParcelFileDescriptor?
    ) {
        unsupportedOperation()
    }

    override fun shutdown() {
        destroy()
    }

    override fun asBinder(): IBinder {
        unsupportedOperation()
    }

    override fun injectInputEvent(event: InputEvent?, sync: Boolean): Boolean {
        unsupportedOperation()
    }

    override fun syncInputTransactions() {
        unsupportedOperation()
    }

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }
}