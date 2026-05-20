package com.example.aicontrolall.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aicontrolall.R

class DevicesFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_secondary, container, false)
        view.findViewById<TextView>(R.id.tvPageTitle).text = "\uD83D\uDD27 设备"
        view.findViewById<TextView>(R.id.tvBack).setOnClickListener {
            (requireActivity() as? MainActivity)?.navigateBack()
        }
        val rvList = view.findViewById<RecyclerView>(R.id.rvList)
        val tvCount = view.findViewById<TextView>(R.id.tvPageCount)
        rvList.layoutManager = LinearLayoutManager(requireContext())

        val devices = listOf(
            DeviceDriver("camera", "摄像头", "输入设备", "内置", listOf("拍照 50MP", "录像 4K", "扫码"), "待机", listOf("帧数据", "分辨率"), "CameraTool", "registered"),
            DeviceDriver("mic", "麦克风", "输入设备", "内置", listOf("录音 48kHz", "VAD"), "待机", listOf("音频流", "音量电平"), "SpeechTool", "registered"),
            DeviceDriver("speaker", "扬声器", "输出设备", "内置", listOf("TTS", "音频播放"), "空闲 60%", listOf("音量"), "SpeechTool", "registered"),
            DeviceDriver("screen", "屏幕", "输出设备", "内置", listOf("UI渲染", "截屏", "亮度"), "1440×3200", listOf("分辨率", "亮度"), "ScreenTool", "planned"),
            DeviceDriver("gps", "GPS定位", "输入设备", "内置", listOf("经纬度", "海拔", "速度"), "待机", listOf("坐标", "精度"), "LocationTool", "planned"),
            DeviceDriver("sensors", "运动传感器", "输入设备组", "内置", listOf("加速度", "角速度", "方向"), "采集 50Hz", listOf("3轴加速度", "角速度"), "SensorTool", "planned"),
            DeviceDriver("bluetooth-audio", "蓝牙耳机", "输入+输出", "外设", listOf("音频输入", "音频输出"), "未连接", listOf(), "BluetoothTool", "planned"),
            DeviceDriver("bluetooth-health", "蓝牙手表", "输入", "外设", listOf("心率", "步数", "血氧"), "未连接", listOf("心率BPM", "步数"), "HealthTool", "planned")
        )
        tvCount.text = "${devices.size} 个"
        rvList.adapter = DeviceAdapter(devices)
        return view
    }
}

class DeviceAdapter(private val devices: List<DeviceDriver>) : RecyclerView.Adapter<DeviceAdapter.VH>() {
    inner class VH(view: View) : RecyclerView.ViewHolder(view)
    private val expanded = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_expandable, parent, false))
    }
    override fun getItemCount() = devices.size
    override fun onBindViewHolder(holder: VH, pos: Int) {
        val d = devices[pos]
        holder.itemView.findViewById<TextView>(R.id.tvItemTitle).text = d.name
        holder.itemView.findViewById<TextView>(R.id.tvItemSub).text = "${d.type} · ${d.status}"
        val tag = holder.itemView.findViewById<TextView>(R.id.tvItemTag)
        tag.text = if (d.mcpToolStatus == "registered") "已注册" else "计划中"
        tag.setTextColor(if (d.mcpToolStatus == "registered") 0xFF06D6A0.toInt() else 0xFFF0A030.toInt())

        val body = holder.itemView.findViewById<View>(R.id.itemBody)
        val chevron = holder.itemView.findViewById<TextView>(R.id.tvChevron)
        if (pos in expanded) { body.visibility = View.VISIBLE; chevron.text = "\u25BC" }
        else { body.visibility = View.GONE; chevron.text = "\u25B6" }
        holder.itemView.findViewById<View>(R.id.itemHead).setOnClickListener {
            if (pos in expanded) expanded.remove(pos) else expanded.add(pos)
            notifyItemChanged(pos)
        }
        val detailRv = body.findViewById<RecyclerView>(R.id.rvDetailFields)
        val details = listOf(
            "类型" to d.type, "类别" to d.category, "状态" to d.status,
            "能力" to d.capabilities.joinToString(", "),
            "数据" to d.dataFields.joinToString(", ").ifEmpty { "(无)" },
            "MCP工具" to "${d.mcpTool ?: "\u2014"} (${d.mcpToolStatus})"
        )
        detailRv.layoutManager = LinearLayoutManager(holder.itemView.context)
        detailRv.adapter = KvAdapter(details)
    }
}
