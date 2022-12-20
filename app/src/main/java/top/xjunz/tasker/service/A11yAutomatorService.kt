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
import android.os.ParcelFileDescriptor
import android.view.InputEvent
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.*
import androidx.test.uiautomator.bridge.UiAutomatorBridge
import kotlinx.coroutines.launch
import top.xjunz.shared.ktx.casted
import top.xjunz.shared.utils.unsupportedOperation
import top.xjunz.tasker.bridge.A11yUiAutomatorBridge
import top.xjunz.tasker.engine.runtime.ComponentInfo
import top.xjunz.tasker.engine.runtime.Event
import top.xjunz.tasker.ktx.isTrue
import top.xjunz.tasker.task.ResidentTaskScheduler
import top.xjunz.tasker.task.event.A11yEventDispatcher
import top.xjunz.tasker.task.inspector.FloatingInspector
import top.xjunz.tasker.task.inspector.InspectorMode
import top.xjunz.tasker.task.inspector.InspectorViewModel
import top.xjunz.tasker.util.ReflectionUtil.requireFieldFromSuperClass
import java.lang.ref.WeakReference

/**
 * @author xjunz 2022/07/12
 */
class A11yAutomatorService : AccessibilityService(), AutomatorService, IUiAutomationConnection,
    LifecycleOwner {

    companion object {

        var FLAG_REQUEST_INSPECTOR_MODE: Boolean = false

        val launchError = MutableLiveData<Throwable>()

        val runningState = MutableLiveData<Boolean>()

        private var instance: WeakReference<A11yAutomatorService>? = null

        fun get() = instance?.get()

        fun require() = checkNotNull(get()) {
            "The A11yAutomatorService is not yet started or is dead!"
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)

    private var startTimestamp: Long = -1

    private var callbacks: AccessibilityServiceHidden.Callbacks? = null

    private lateinit var uiAutomationHidden: UiAutomationHidden

    private lateinit var inspectorViewModel: InspectorViewModel

    private val uiAutomation: UiAutomation get() = uiAutomationHidden.casted()

    lateinit var inspector: FloatingInspector

    lateinit var taskScheduler: ResidentTaskScheduler

    override val isRunning get() = runningState.isTrue

    override val uiAutomatorBridge: UiAutomatorBridge by lazy {
        A11yUiAutomatorBridge(this, uiAutomation)
    }

    private var launchedInInspectorMode = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            launchedInInspectorMode = FLAG_REQUEST_INSPECTOR_MODE
            if (!launchedInInspectorMode) {
                uiAutomationHidden = UiAutomationHidden(mainLooper, this)
                uiAutomationHidden.connect()
            } else {
                taskScheduler = ResidentTaskScheduler(mainLooper)
            }
            instance = WeakReference(this)
            runningState.value = true
            startTimestamp = System.currentTimeMillis()
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        } catch (t: Throwable) {
            t.printStackTrace()
            launchError.value = t
            destroy()
        }
    }

    fun destroyFloatingInspector() {
        inspector.dismiss()
        if (launchedInInspectorMode) {
            disableSelf()
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
            // recreate the inspector
            inspector.dismiss()
            inspectorViewModel.onConfigurationChanged()
            inspector = FloatingInspector(this, inspectorViewModel)
            inspector.show()
        }
    }

    private val componentInfo = ComponentInfo()

    private val a11yEventDispatcher: A11yEventDispatcher by lazy {
        A11yEventDispatcher { events ->
            val event = events.find {
                it.type == Event.EVENT_ON_CONTENT_CHANGED || it.type == Event.EVENT_ON_PACKAGE_ENTERED
            }
            if (event != null && componentInfo != event.componentInfo) {
                componentInfo.copyFrom(event.componentInfo)
                inspectorViewModel.currentComp.value = componentInfo
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (isInspectorShown()) {
            lifecycleScope.launch {
                a11yEventDispatcher.processAccessibilityEvent(event)
            }
        }
        callbacks?.onAccessibilityEvent(event)
    }

    override fun onInterrupt() {

    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        instance?.clear()
        runningState.value = false
        if (isInspectorShown()) inspector.dismiss()
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
        if (isRunning && !launchedInInspectorMode) {
            uiAutomationHidden.disconnect()
        }
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