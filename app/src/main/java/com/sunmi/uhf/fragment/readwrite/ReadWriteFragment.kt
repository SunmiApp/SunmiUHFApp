package com.sunmi.uhf.fragment.readwrite

import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.Observer
import com.sunmi.rfid.RFIDManager
import com.sunmi.rfid.ReaderCall
import com.sunmi.rfid.constant.CMD
import com.sunmi.rfid.constant.ParamCts
import com.sunmi.rfid.entity.DataParameter
import com.sunmi.uhf.App
import com.sunmi.uhf.R
import com.sunmi.uhf.constants.Config
import com.sunmi.uhf.constants.EventConstant
import com.sunmi.uhf.databinding.FragmentReadWriteBinding
import com.sunmi.uhf.dialog.SureBackDialog
import com.sunmi.uhf.event.SimpleViewEvent
import com.sunmi.uhf.fragment.ReadBaseFragment
import com.sunmi.uhf.utils.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * @ClassName: ReadWriteFragment
 * @Description: 快读读取 页面
 * @Author: clh
 * @CreateDate: 20-9-8 下午4:30
 * @UpdateDate: 20-9-8 下午4:30
 */
class ReadWriteFragment : ReadBaseFragment<FragmentReadWriteBinding>() {
    var dialog: SureBackDialog? = null
    lateinit var vm: ReadWriteViewModel
    private var isLoop = false
    private var allCount = 0

    override fun getLayoutResource() = R.layout.fragment_read_write

    override fun initVM() {
        vm = getViewModel(ReadWriteViewModel::class.java)
        binding.vm = vm
    }

    override fun initView() {
        binding.chronometerView.base = SystemClock.elapsedRealtime()
    }

    override fun initData() {
        super.initData()
        vm.start.observe(viewLifecycleOwner, Observer { startStop(it) })
    }


    override fun onSimpleViewEvent(event: SimpleViewEvent) {
        super.onSimpleViewEvent(event)
        when (event.event) {
            EventConstant.EVENT_BACK -> {
                showSureDialog()
            }
        }

    }

    /**
     * 点击返回健后，弹出二次确认弹窗
     * 点击退出 ，退出页面
     */
    private fun showSureDialog() {
        val showing = (dialog?.isAdded ?: false || dialog?.dialog?.isShowing ?: false)
        if (showing) dialog?.dismiss()
        if (dialog == null) {
            dialog = SureBackDialog.newInstance(null)
        }
        dialog?.listener = object : (() -> Unit) {
            override fun invoke() {
                dialog?.dismiss()
                performBackClick()
            }
        }
        dialog?.show(parentFragmentManager, SureBackDialog::class.java.name)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dialog?.dismiss()
    }

    private fun startStop(en: Boolean) {
        if (en) {
            tidList.clear()
            tagList.clear()
            allCount = 0
            vm.labelNum.value = 0
            vm.totalLabelNum.value = 0
            vm.speed.value = 0f
            binding.chronometerView.base = SystemClock.elapsedRealtime()
            binding.chronometerView.start()
            notifyTagDataChange()
            start()
        } else {
            binding.chronometerView.stop()
            stop()
        }
    }

    override fun handleBottomStart() {
        vm.start.value = true
    }

    override fun handleBottomStop() {
        vm.start.value = false
    }

    override fun start() {
        super.start()
        if (!isLoop) {
            mainScope.launch(Dispatchers.IO) {
                RFIDManager.getInstance().helper.apply {
                    when (App.getPref().getParam(Config.KEY_LABEL, Config.DEF_LABEL)) {
                        0 -> {
                            // 6C标签盘存
                            registerReaderCall(call)
                            realTimeInventory(20)
                            isLoop = true
                        }
                        1 -> {
                            // 6B标签盘存
                            registerReaderCall(call)
                            iso180006BInventory()
                            isLoop = true
                        }
                        else -> {
                            LogUtils.e("darren", "error label index")
                        }
                    }
                }
            }
        }
    }

    override fun stop() {
        super.stop()
        if (isLoop) {
            mainScope.launch(Dispatchers.IO) {
                RFIDManager.getInstance().helper.apply {
                    inventory(1)
                    unregisterReaderCall()
                    isLoop = false
                }
            }
        }
    }

    override fun onCallSuccess(cmd: Byte, params: DataParameter?) {
        when (cmd) {
            CMD.REAL_TIME_INVENTORY -> {
                isLoop = false
                if (state) {
                    start()
                }
            }
            CMD.ISO18000_6B_INVENTORY -> {
                isLoop = false
                if (state) {
                    start()
                }
            }
            else -> {
                LogUtils.d("darren", "other success.")
            }
        }
    }

    override fun onCallTag(cmd: Byte, state: Byte, tag: DataParameter?) {
        if (tag == null) return
        playTips()
        when (cmd) {
            CMD.REAL_TIME_INVENTORY -> {
                // 6C标签盘存
                allCount++
                val epc = tag.getString(ParamCts.TAG_EPC) ?: ""
                LogUtils.i("darren", "found tag:$epc")
                val index = tidList.indexOf(epc)
                if (index != -1) {
                    tagList[index] = tag
                } else {
                    tidList.add(0, epc)
                    tagList.add(0, tag)
                }
                notifyTagDataChange()
            }
            CMD.ISO18000_6B_INVENTORY -> {
                allCount++
                val uid = tag.getString(ParamCts.TAG_UID) ?: ""
                LogUtils.i("darren", "found tag:$uid")
                val index: Int = tidList.indexOf(uid)
                if (index != -1) {
                    tagList[index] = tag
                } else {
                    tidList.add(0, uid)
                    tagList.add(0, tag)
                }
                notifyTagDataChange()
            }
            else -> {
                LogUtils.d("darren", "other found tag.")
            }
        }
    }

    override fun onCallFailed(cmd: Byte, errorCode: Byte, msg: String?) {
        when (cmd) {
            CMD.REAL_TIME_INVENTORY -> {
                isLoop = false
                if (state) {
                    start()
                }
            }
            CMD.ISO18000_6B_INVENTORY -> {
                isLoop = false
                if (state) {
                    start()
                }
            }
            else -> {
                LogUtils.d("darren", "other failed.")
            }
        }
    }

    private fun notifyTagDataChange() {
        mainScope.launch {
            vm.labelNum.value = tidList.size
            vm.totalLabelNum.value = allCount
            val time = (SystemClock.elapsedRealtime() - binding.chronometerView.base) / 1000
            if (time < 1) {
                vm.speed.value = allCount.toFloat()
            } else {
                vm.speed.value = allCount * 10 / time / 10f
            }
        }
    }

    companion object {
        fun newInstance(args: Bundle?) = ReadWriteFragment()
            .apply { arguments = args }
    }
}
