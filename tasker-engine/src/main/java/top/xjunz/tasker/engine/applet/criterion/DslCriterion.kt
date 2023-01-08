/*
 * Copyright (c) 2022 xjunz. All rights reserved.
 */

package top.xjunz.tasker.engine.applet.criterion

/**
 * @author xjunz 2022/08/14
 */
internal class DslCriterion<T : Any, V : Any> : Criterion<T, V>() {

    lateinit var matcher: ((T, V) -> Boolean)

    override fun matchTarget(target: T, value: V): Boolean {
        return matcher(target, value)
    }

    override var valueType: Int = VAL_TYPE_IRRELEVANT
}