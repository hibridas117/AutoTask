package top.xjunz.tasker.ui.task.selector.option

import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import top.xjunz.tasker.R
import top.xjunz.tasker.databinding.DialogArgumentsEditorBinding
import top.xjunz.tasker.databinding.ItemArgumentEditorBinding
import top.xjunz.tasker.engine.applet.base.Applet
import top.xjunz.tasker.ktx.*
import top.xjunz.tasker.task.applet.option.AppletOption
import top.xjunz.tasker.task.applet.option.ValueDescriptor
import top.xjunz.tasker.ui.base.BaseDialogFragment
import top.xjunz.tasker.ui.base.inlineAdapter
import top.xjunz.tasker.ui.common.TextEditorDialog
import top.xjunz.tasker.ui.task.editor.FlowEditorDialog
import top.xjunz.tasker.ui.task.editor.GlobalFlowEditorViewModel
import top.xjunz.tasker.util.AntiMonkeyUtil.setAntiMoneyClickListener
import java.util.*

/**
 * @author xjunz 2022/11/22
 */
class ArgumentsEditorDialog : BaseDialogFragment<DialogArgumentsEditorBinding>() {

    override val isFullScreen: Boolean = false

    private class InnerViewModel : ViewModel() {

        lateinit var option: AppletOption

        lateinit var applet: Applet

        lateinit var doOnCompletion: () -> Unit

        val onItemChanged = MutableLiveData<ValueDescriptor>()

        fun checkForUnspecifiedArgument(): Int {
            option.arguments.forEachIndexed { which, arg ->
                val isValueSet = applet.value != null
                if (arg.isValueOnly && !isValueSet) {
                    return which
                }
                val isReferenceSet = applet.references[which] != null
                if (arg.isReferenceOnly && !isReferenceSet) {
                    return which
                }
                if (arg.isTolerant && !isValueSet && !isReferenceSet) {
                    return which
                }
            }
            return -1
        }
    }

    private inline val option get() = viewModel.option

    private inline val applet get() = viewModel.applet

    private val viewModel by viewModels<InnerViewModel>()

    private val globalViewModel by activityViewModels<GlobalFlowEditorViewModel>()

    private fun showValueInputDialog(which: Int, arg: ValueDescriptor) {
        TextEditorDialog().configEditText { et ->
            et.configInputType(arg.type, true)
            et.maxLines = 5
        }.setCaption(option.helpText).init(arg.name, applet.value?.toString()) set@{
            val parsed = arg.parseValueFromInput(it) ?: return@set R.string.error_mal_format.str
            globalViewModel.tracer.setValue(applet, which, parsed)
            viewModel.onItemChanged.value = arg
            return@set null
        }.show(parentFragmentManager)
    }

    private fun showReferenceSelectorDialog(whichArg: Int, arg: ValueDescriptor, id: String?) {
        FlowEditorDialog().init(globalViewModel.root, true)
            .setReferenceToSelect(applet, arg, id)
            .doOnReferenceSelected { refid ->
                globalViewModel.tracer.setReference(applet, arg, whichArg, refid)
                viewModel.onItemChanged.value = arg
            }.show(childFragmentManager)
    }

    private val adapter by lazy {
        inlineAdapter(option.arguments, ItemArgumentEditorBinding::class.java, {
            binding.root.setAntiMoneyClickListener {
                val which = adapterPosition
                val arg = option.arguments[which]
                if (arg.isTolerant) {
                    val popup = PopupMenu(requireContext(), it, Gravity.END)
                    popup.menu.add(R.string.refer_to.format(arg.name))
                    popup.menu.add(R.string.specify_value.format(arg.name))
                    popup.show()
                    popup.setOnMenuItemClickListener set@{ item ->
                        when (popup.menu.indexOf(item)) {
                            0 -> showReferenceSelectorDialog(which, arg, null)
                            1 -> showValueInputDialog(which, arg)
                        }
                        return@set true
                    }
                } else if (arg.isReferenceOnly) {
                    showReferenceSelectorDialog(which, arg, applet.references[which])
                } else if (arg.isValueOnly) {
                    showValueInputDialog(which, arg)
                }
            }
            binding.tvValue.setAntiMoneyClickListener {
                val position = adapterPosition
                val arg = option.arguments[position]
                val refid = applet.references.getValue(position)
                TextEditorDialog().setCaption(R.string.prompt_set_refid.text).configEditText {
                    it.setMaxLength(Applet.MAX_REFERENCE_ID_LENGTH)
                }.init(R.string.edit_refid.text, refid) {
                    if (it == refid) return@init null
                    if (!globalViewModel.isRefidLegalForSelections(it)) {
                        return@init R.string.error_tag_exists.text
                    }
                    // This applet may be not attached to the root
                    globalViewModel.tracer.renameReference(applet, position, it)
                    globalViewModel.renameRefidInRoot(Collections.singleton(refid), it)
                    viewModel.onItemChanged.value = arg
                    return@init null
                }.show(childFragmentManager)
            }
        }) { binding, pos, arg ->
            binding.tvTitle.text = arg.name
            binding.tvValue.isEnabled = true
            binding.tvValue.isClickable = false
            binding.tvValue.background = null
            if (!arg.isValueOnly && applet.references.containsKey(pos)) {
                val refid = applet.references.getValue(pos)
                // Not value only and the reference is available
                binding.tvValue.text = refid.underlined().foreColored()
                binding.tvValue.setDrawableStart(R.drawable.ic_baseline_link_24)
                binding.tvValue.isClickable = true
                binding.tvValue.background =
                    android.R.attr.selectableItemBackground.resolvedId.getDrawable()
            } else if (!arg.isReferenceOnly && applet.value != null) {
                // Not reference only and the value is available
                binding.tvValue.text = option.describe(applet)
                binding.tvValue.setDrawableStart(R.drawable.ic_baseline_text_fields_24)
            } else {
                binding.tvValue.text = R.string.unspecified.text.italic()
                binding.tvValue.setDrawableStart(View.NO_ID)
                binding.tvValue.isEnabled = false
            }
            if (arg.isValueOnly) {
                binding.ivEnter.setImageResource(R.drawable.ic_edit_24dp)
            } else {
                binding.ivEnter.setImageResource(R.drawable.ic_baseline_chevron_right_24)
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        globalViewModel.tracer.revokeAll()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvArgument.adapter = adapter
        binding.tvTitle.text = option.currentTitle
        binding.btnCancel.setOnClickListener {
            globalViewModel.tracer.revokeAll()
            dismiss()
        }
        binding.btnComplete.setAntiMoneyClickListener {
            val illegal = viewModel.checkForUnspecifiedArgument()
            if (illegal == -1) {
                viewModel.doOnCompletion()
                globalViewModel.tracer.getReferenceChangedApplets().forEach {
                    globalViewModel.onAppletChanged.value = it
                }
                globalViewModel.tracer.reset()
                dismiss()
            } else {
                val item = binding.rvArgument.findViewHolderForAdapterPosition(illegal)?.itemView
                item?.shake()
                toast(R.string.format_not_specified.format(option.arguments[illegal].name))
            }
        }
        observeTransient(viewModel.onItemChanged) {
            adapter.notifyItemChanged(option.arguments.indexOf(it), true)
        }
    }

    fun setAppletOption(applet: Applet, option: AppletOption) = doWhenCreated {
        viewModel.option = option
        viewModel.applet = applet
    }

    fun doOnCompletion(block: () -> Unit) = doWhenCreated {
        viewModel.doOnCompletion = block
    }
}