package com.example.aicontrolall.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aicontrolall.R
import com.example.aicontrolall.memory.MemoryStore
import com.example.aicontrolall.memory.models.Memory

class MemoryFragment : Fragment() {
    private var memoryStore: MemoryStore? = null
    private lateinit var rvList: RecyclerView
    private lateinit var tvCount: TextView

    fun setMemoryStore(store: MemoryStore) { memoryStore = store }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_secondary, container, false)
        rvList = view.findViewById(R.id.rvList)
        tvCount = view.findViewById(R.id.tvPageCount)
        view.findViewById<TextView>(R.id.tvPageTitle).text = "🧠 记忆库"
        view.findViewById<TextView>(R.id.tvBack).setOnClickListener {
            (requireActivity() as? MainActivity)?.navigateBack()
        }
        rvList.layoutManager = LinearLayoutManager(requireContext())
        loadData()
        return view
    }

    override fun onResume() { super.onResume(); loadData() }

    private fun loadData() {
        val memories = memoryStore?.getRecent(50) ?: emptyList()
        tvCount.text = "${memories.size} 个"
        rvList.adapter = MemoryAdapter(memories,
            onEdit = { memory ->
                Toast.makeText(requireContext(), "编辑记忆: ${memory.content.take(30)}...", Toast.LENGTH_SHORT).show()
            },
            onDelete = { memory ->
                memoryStore?.remove(memory.id)
                loadData()
            }
        )
    }
}

class MemoryAdapter(
    private val memories: List<Memory>,
    private val onEdit: (Memory) -> Unit,
    private val onDelete: (Memory) -> Unit
) : RecyclerView.Adapter<MemoryAdapter.VH>() {
    inner class VH(view: View) : RecyclerView.ViewHolder(view)
    private val expanded = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_expandable, parent, false))
    }
    override fun getItemCount() = memories.size
    override fun onBindViewHolder(holder: VH, pos: Int) {
        val m = memories[pos]
        val preview = if (m.content.length > 40) m.content.take(40) + "..." else m.content
        holder.itemView.findViewById<TextView>(R.id.tvItemTitle).text = preview
        holder.itemView.findViewById<TextView>(R.id.tvItemSub).text = "使用次数: ${m.usageCount}"
        val tag = if (m.tags.isNotEmpty()) m.tags.split(",").firstOrNull()?.trim() ?: m.target else m.target
        holder.itemView.findViewById<TextView>(R.id.tvItemTag).text = tag
        val body = holder.itemView.findViewById<View>(R.id.itemBody)
        val chevron = holder.itemView.findViewById<TextView>(R.id.tvChevron)
        body.visibility = if (pos in expanded) View.VISIBLE else View.GONE
        chevron.text = if (pos in expanded) "▼" else "▶"
        holder.itemView.findViewById<View>(R.id.itemHead).setOnClickListener {
            if (pos in expanded) expanded.remove(pos) else expanded.add(pos)
            notifyItemChanged(pos)
        }
        // Detail fields
        val detailRv = body.findViewById<RecyclerView>(R.id.rvDetailFields)
        val details = listOf(
            "内容" to m.content,
            "目标" to m.target,
            "标签" to m.tags,
            "使用次数" to m.usageCount.toString(),
            "记录时间" to m.createdAt.toString()
        )
        detailRv.layoutManager = LinearLayoutManager(holder.itemView.context)
        detailRv.adapter = KvAdapter(details)
        // Actions
        val actions = holder.itemView.findViewById<ViewGroup>(R.id.itemActions)
        actions.removeAllViews()
        val btnEdit = TextView(holder.itemView.context).apply {
            text = "编辑"; setPadding(24, 12, 24, 12)
            setTextColor(0xFFE0E6F0.toInt()); setBackgroundColor(0xFF1A2332.toInt())
            setOnClickListener { onEdit(m) }
        }
        val btnDelete = TextView(holder.itemView.context).apply {
            text = "删除"; setPadding(24, 12, 24, 12)
            setTextColor(0xFFFF5C5C.toInt()); setBackgroundColor(0xFF1A2332.toInt())
            setOnClickListener { onDelete(m) }
        }
        actions.addView(btnEdit); actions.addView(btnDelete)
    }
}
