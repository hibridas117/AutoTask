/*
 * Copyright (c) 2022 xjunz. All rights reserved.
 */

package top.xjunz.tasker.task.applet.option.registry

import android.view.accessibility.AccessibilityNodeInfo
import top.xjunz.shared.utils.illegalArgument
import top.xjunz.tasker.R
import top.xjunz.tasker.engine.applet.base.*
import top.xjunz.tasker.task.applet.anno.AppletCategory
import top.xjunz.tasker.task.applet.flow.*
import top.xjunz.tasker.task.applet.option.AppletOption

open class FlowOptionRegistry : AppletOptionRegistry(ID_FLOW_OPTION_REGISTRY) {

    companion object {

        private const val ID_FLOW_OPTION_REGISTRY = 0
        const val ID_EVENT_FILTER_REGISTRY: Int = 0xF
        const val ID_PKG_OPTION_REGISTRY = 0x10
        const val ID_UI_OBJECT_OPTION_REGISTRY = 0x11
        const val ID_TIME_OPTION_REGISTRY = 0x12
        const val ID_GLOBAL_OPTION_REGISTRY = 0x13
        const val ID_NOTIFICATION_OPTION_REGISTRY = 0x14

        const val ID_GLOBAL_ACTION_REGISTRY = 0x50
        const val ID_UI_OBJECT_ACTION_REGISTRY = 0x51
        const val ID_CONTROL_ACTION_REGISTRY = 0x52
        const val ID_APP_ACTION_REGISTRY = 0x53
        const val ID_TEXT_ACTION_REGISTRY = 0x54
    }

    private inline fun <reified F : Flow> presetFlowOption(
        appletId: Int,
        title: Int
    ): AppletOption {
        return appletOption(title) {
            F::class.java.newInstance()
        }.also {
            it.appletId = appletId
        }
    }

    private inline fun <reified F : Flow> flowOption(title: Int): AppletOption {
        return presetFlowOption<F>(-1, title)
    }


    fun getPeerOptions(flow: ControlFlow, before: Boolean): Array<AppletOption> {
        // Regex("验证码.*?(\\d+?)")
        return when (flow) {
            is If -> if (before) emptyArray() else arrayOf(doFlow)
            is When -> if (before) emptyArray() else arrayOf(ifFlow, doFlow)
            is Else -> if (before) emptyArray() else arrayOf(ifFlow)
            is Do -> if (before) emptyArray() else arrayOf(ifFlow, elseIfFlow, elseFlow)
            else -> illegalArgument("control flow", flow)
        }
    }

    /**
     * Applet flow is a container flow whose child has the same target.
     */
    val criterionFlowOptions: Array<AppletOption> by lazy {
        arrayOf(componentFlow, uiObjectFlow, timeFlow, notificationFlow, globalInfoFlow)
    }

    val actionFlowOptions: Array<AppletOption> by lazy {
        arrayOf(
            globalActionFlow,
            uiObjectActionFlow,
            textActionFlow,
            appActionFlow,
            controlActionFlow
        )
    }

    @AppletCategory(0x0000)
    val rootFlow = flowOption<RootFlow>(AppletOption.TITLE_NONE)

    @AppletCategory(0x0001)
    val whenFlow = flowOption<When>(R.string._when)

    @AppletCategory(0x0002)
    val ifFlow = flowOption<If>(R.string._if)

    @AppletCategory(0x0003)
    val doFlow = flowOption<Do>(R.string._do)

    @AppletCategory(0x0004)
    val elseIfFlow = flowOption<ElseIf>(R.string.else_if)

    @AppletCategory(0x0005)
    val elseFlow = flowOption<Else>(R.string._else)

    @AppletCategory(0x0005)
    val containerFlow = flowOption<ContainerFlow>(AppletOption.TITLE_NONE)

    @AppletCategory(0x000F)
    val eventFlow = presetFlowOption<PhantomFlow>(ID_EVENT_FILTER_REGISTRY, R.string.event)

    @AppletCategory(0x0010)
    val componentFlow = presetFlowOption<PackageFlow>(ID_PKG_OPTION_REGISTRY, R.string.current_app)
        .withResult<String>(R.string.package_name)

    @AppletCategory(0x0011)
    val uiObjectFlow =
        presetFlowOption<UiObjectFlow>(ID_UI_OBJECT_OPTION_REGISTRY, R.string.ui_object_exists)
            .withResult<AccessibilityNodeInfo>(R.string.ui_object)
            .withResult<String>(R.string.matched_ui_object_text)

    @AppletCategory(0x0012)
    val timeFlow = presetFlowOption<TimeFlow>(ID_TIME_OPTION_REGISTRY, R.string.current_time)

    @AppletCategory(0x0013)
    val globalInfoFlow =
        presetFlowOption<PhantomFlow>(ID_GLOBAL_OPTION_REGISTRY, R.string.device_status)

    @AppletCategory(0x0014)
    val notificationFlow = presetFlowOption<NotificationFlow>(
        ID_NOTIFICATION_OPTION_REGISTRY, R.string.current_notification
    )

    @AppletCategory(0x0020)
    val globalActionFlow =
        presetFlowOption<PhantomFlow>(ID_GLOBAL_ACTION_REGISTRY, R.string.global_actions)

    @AppletCategory(0x0021)
    val uiObjectActionFlow =
        presetFlowOption<PhantomFlow>(ID_UI_OBJECT_ACTION_REGISTRY, R.string.ui_object_operations)

    @AppletCategory(0x0022)
    val textActionFlow =
        presetFlowOption<PhantomFlow>(ID_TEXT_ACTION_REGISTRY, R.string.text_operations)

    @AppletCategory(0x0023)
    val appActionFlow =
        presetFlowOption<PhantomFlow>(ID_APP_ACTION_REGISTRY, R.string.app_operations)

    @AppletCategory(0x0024)
    val controlActionFlow =
        presetFlowOption<PhantomFlow>(ID_CONTROL_ACTION_REGISTRY, R.string.control_actions)

}