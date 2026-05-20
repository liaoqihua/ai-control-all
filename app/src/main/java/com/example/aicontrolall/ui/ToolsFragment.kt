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
import com.example.aicontrolall.mcp.McpGateway
import com.example.aicontrolall.mcp.McpTool

class ToolsFragment : Fragment() {
    private var mcpGateway: McpGateway? = null
    private lateinit var rvList: RecyclerView
    private lateinit var tvCount: TextView

    fun setMcpGateway(gateway: McpGateway) { mcpGateway = gateway }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_secondary, container, false)
        rvList = view.findViewById(R.id.rvList)
        tvCount = view.findViewById(R.id.tvPageCount)
        view.findViewById<TextView>(R.id.tvPageTitle).text = "🔧 工具集"
        view.findViewById<TextView>(R.id.tvBack).setOnClickListener {
            (requireActivity() as? MainActivity)?.navigateBack()
        }
        rvList.layoutManager = LinearLayoutManager(requireContext())
        loadData()
        return view
    }

    override fun onResume() { super.onResume(); loadData() }

    private fun loadData() {
        val tools = mcpGateway?.listTools() ?: emptyList()
        tvCount.text = "${tools.size} 个"
        rvList.adapter = ToolsAdapter(tools)
    }
}

class ToolsAdapter(
    private val tools: List<McpTool>
) : RecyclerView.Adapter<ToolsAdapter.VH>() {
    inner class VH(view: View) : RecyclerView.ViewHolder(view)
    private val expanded = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_expandable, parent, false))
    }
    override fun getItemCount() = tools.size
    override fun onBindViewHolder(holder: VH, pos: Int) {
        val t = tools[pos]
        holder.itemView.findViewById<TextView>(R.id.tvItemTitle).text = t.name
        holder.itemView.findViewById<TextView>(R.id.tvItemSub).text = t.description
        holder.itemView.findViewById<TextView>(R.id.tvItemTag).text = "● 已注册"
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
            "名称" to t.name,
            "描述" to t.description,
            "参数定义" to t.parameters
        )
        detailRv.layoutManager = LinearLayoutManager(holder.itemView.context)
        detailRv.adapter = KvAdapter(details)
        // No actions for tools currently
        val actions = holder.itemView.findViewById<ViewGroup>(R.id.itemActions)
        actions.removeAllViews()
    }
}
