package top.xjunz.tasker.engine.runtime

import android.util.SparseArray
import top.xjunz.shared.ktx.casted
import top.xjunz.tasker.engine.AutomatorTask
import top.xjunz.tasker.engine.applet.base.Applet

/**
 * The context which is needed when executing an [Applet].
 *
 * @author xjunz 2022/08/04
 */
class TaskContext(
    /**
     * The task where the applet is defined.
     */
    val task: AutomatorTask,
    val events: Array<Event>
) {

    lateinit var hitEvent: Event

    /**
     * All applets with same id share the same argument.
     */
    val arguments = SparseArray<Any>()

    /**
     * Get the argument from the registry or initialize the argument and store it.
     */
    inline fun <T : Any> getOrPutArgument(id: Int, defValue: () -> T): T {
        var arg = arguments[id]
        if (arg == null) {
            arg = defValue()
            arguments.put(id, arg)
        }
        return arg.casted()
    }
}