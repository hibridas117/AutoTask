/*
 * Copyright (c) 2022 xjunz. All rights reserved.
 */

package top.xjunz.tasker.task.applet.option.registry

import top.xjunz.tasker.R
import top.xjunz.tasker.bridge.BatteryManagerBridge
import top.xjunz.tasker.bridge.DisplayManagerBridge
import top.xjunz.tasker.engine.applet.criterion.NumberRangeCriterion
import top.xjunz.tasker.engine.applet.criterion.PropertyCriterion
import top.xjunz.tasker.ktx.format
import top.xjunz.tasker.task.applet.anno.AppletCategory

/**
 * @author xjunz 2022/11/10
 */
class GlobalInfoOptionRegistry(id: Int) : AppletOptionRegistry(id) {



    @AppletCategory(0x0000)
    val isScreenPortrait = invertibleAppletOption(R.string.screen_orientation_portrait) {
        PropertyCriterion<Unit> {
            val realSize = DisplayManagerBridge.size
            realSize.x > realSize.y
        }
    }

    @AppletCategory(0x0010)
    val isBatteryCharging = invertibleAppletOption(R.string.is_charging) {
        PropertyCriterion<Unit> {
            BatteryManagerBridge.isCharging
        }
    }

    @AppletCategory(0x0011)
    val batteryCapacityRange = invertibleAppletOption(R.string.in_battery_capacity_range) {
        NumberRangeCriterion<Unit, Int> {
            BatteryManagerBridge.capacity
        }
    }.withValueDescriber<Collection<Int?>> {
        val first = it.firstOrNull()
        val last = it.lastOrNull()
        if (first == null && last != null) {
            R.string.format_percent_less_than.format(last)
        } else if (last == null && first != null) {
            R.string.format_percent_larger_than.format(first)
        } else if (last == first) {
            "$first%"
        } else {
            R.string.format_percent_range.format(first, last)
        }
    }

}