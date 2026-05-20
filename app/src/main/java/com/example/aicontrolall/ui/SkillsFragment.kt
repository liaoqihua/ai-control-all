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
import com.example.aicontrolall.memory.SkillStore
import com.example.aicontrolall.memory.models.Skill

class SkillsFragment : Fragment() {
    private var skillStore: SkillStore? = null
    private lateinit var rvList: RecyclerView
    private lateinit var tvCount: TextView

    fun setSkillStore(store: SkillStore) { skillStore = store }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_secondary, container, false)
        rvList = view.findViewById(R.id.rvList)
        tvCount = view.findViewById(R.id.tvPageCount)
        view.findViewById<TextView>(R.id.tvPageTitle).text = "\u2605 技能"
        view.findViewById<TextView>(R.id.tvBack).setOnClickListener {
            (requireActivity() as? MainActivity)?.navigateBack()
        }
        rvList.layoutManager = LinearLayoutManager(requireContext())
        loadData()
        return view
    }

    override fun onResume() { super.onResume(); loadData() }

    private fun loadData() {
        val skills = skillStore?.getAll(50) ?: emptyList()
        tvCount.text = "${skills.size} 个"
        rvList.adapter = SkillAdapter(skills)
    }
}

class SkillAdapter(private val skills: List<Skill>) : RecyclerView.Adapter<SkillAdapter.VH>() {
    inner class VH(view: View) : RecyclerView.ViewHolder(view)
    private val expanded = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_expandable, parent, false))
    }
    override fun getItemCount() = skills.size
    override fun onBindViewHolder(holder: VH, pos: Int) {
        val s = skills[pos]
        holder.itemView.findViewById<TextView>(R.id.tvItemTitle).text = s.name
        holder.itemView.findViewById<TextView>(R.id.tvItemSub).text = s.description
        val tag = holder.itemView.findViewById<TextView>(R.id.tvItemTag)
        val pct = (s.confidence * 100).toInt()
        tag.text = "$pct%"
        tag.setTextColor(when { pct >= 70 -> 0xFF06D6A0.toInt(); pct >= 40 -> 0xFF00D4FF.toInt(); else -> 0xFFF0A030.toInt() })

        val body = holder.itemView.findViewById<View>(R.id.itemBody)
        val chevron = holder.itemView.findViewById<TextView>(R.id.tvChevron)
        if (pos in expanded) { body.visibility = View.VISIBLE; chevron.text = "\u25BC" }
        else { body.visibility = View.GONE; chevron.text = "\u25B6" }
        holder.itemView.findViewById<View>(R.id.itemHead).setOnClickListener {
            if (pos in expanded) expanded.remove(pos) else expanded.add(pos)
            notifyItemChanged(pos)
        }
        val detailRv = body.findViewById<RecyclerView>(R.id.rvDetailFields)
        val details = listOf("版本" to "v${s.version}", "使用次数" to "${s.usageCount}", "步骤" to s.steps, "陷阱" to s.pitfalls.ifEmpty { "(无)" })
        detailRv.layoutManager = LinearLayoutManager(holder.itemView.context)
        detailRv.adapter = KvAdapter(details)
        val actions = holder.itemView.findViewById<ViewGroup>(R.id.itemActions)
        actions.removeAllViews()
        actions.addView(TextView(holder.itemView.context).apply {
            text = "编辑"; setPadding(24, 12, 24, 12); setTextColor(0xFFE0E6F0.toInt()); setBackgroundColor(0xFF1A2332.toInt())
        })
        actions.addView(TextView(holder.itemView.context).apply {
            text = if (s.confidence < 0.5f) "删除" else "禁用"
            setPadding(24, 12, 24, 12); setTextColor(0xFFFF5C5C.toInt()); setBackgroundColor(0xFF1A2332.toInt())
        })
    }
}
