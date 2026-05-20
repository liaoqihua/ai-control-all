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
import com.example.aicontrolall.memory.SessionStore
import com.example.aicontrolall.memory.models.Session

class HistoryFragment : Fragment() {
    private var sessionStore: SessionStore? = null
    private lateinit var rvList: RecyclerView
    private lateinit var tvCount: TextView

    fun setSessionStore(store: SessionStore) { sessionStore = store }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_secondary, container, false)
        rvList = view.findViewById(R.id.rvList)
        tvCount = view.findViewById(R.id.tvPageCount)
        view.findViewById<TextView>(R.id.tvPageTitle).text = "🕑 历史会话"
        view.findViewById<TextView>(R.id.tvBack).setOnClickListener {
            (requireActivity() as? MainActivity)?.navigateBack()
        }
        rvList.layoutManager = LinearLayoutManager(requireContext())
        loadData()
        return view
    }

    override fun onResume() { super.onResume(); loadData() }

    private fun loadData() {
        val sessions = buildSessions()
        tvCount.text = "${sessions.size} 个"
        rvList.adapter = HistoryAdapter(sessions) { session ->
            Toast.makeText(requireContext(), "继续对话: ${session.title}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildSessions(): List<Session> {
        val raw = sessionStore?.getAllSessions() ?: emptyList()
        return raw.map { (id, title) ->
            Session(id = id, title = title, createdAt = System.currentTimeMillis())
        }
    }
}

class HistoryAdapter(
    private val sessions: List<Session>,
    private val onContinue: (Session) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.VH>() {
    inner class VH(view: View) : RecyclerView.ViewHolder(view)
    private val expanded = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_expandable, parent, false))
    }
    override fun getItemCount() = sessions.size
    override fun onBindViewHolder(holder: VH, pos: Int) {
        val s = sessions[pos]
        holder.itemView.findViewById<TextView>(R.id.tvItemTitle).text = s.title.ifEmpty { "(无标题)" }
        holder.itemView.findViewById<TextView>(R.id.tvItemSub).text = "${s.createdAt}"
        holder.itemView.findViewById<TextView>(R.id.tvItemTag).text = if (s.summary.isNotEmpty()) "有摘要" else ""
        val body = holder.itemView.findViewById<View>(R.id.itemBody)
        val chevron = holder.itemView.findViewById<TextView>(R.id.tvChevron)
        body.visibility = if (pos in expanded) View.VISIBLE else View.GONE
        chevron.text = if (pos in expanded) "▼" else "▶"
        holder.itemView.findViewById<View>(R.id.itemHead).setOnClickListener {
            if (pos in expanded) expanded.remove(pos) else expanded.add(pos)
            notifyItemChanged(pos)
        }
        // Populate detail fields
        val detailRv = body.findViewById<RecyclerView>(R.id.rvDetailFields)
        val details = listOf("ID" to s.id, "摘要" to s.summary)
        detailRv.layoutManager = LinearLayoutManager(holder.itemView.context)
        detailRv.adapter = KvAdapter(details)
        // Actions
        val actions = holder.itemView.findViewById<ViewGroup>(R.id.itemActions)
        actions.removeAllViews()
        val btnContinue = TextView(holder.itemView.context).apply {
            text = "继续对话"; setPadding(24, 12, 24, 12)
            setTextColor(0xFFE0E6F0.toInt()); setBackgroundColor(0xFF1A2332.toInt())
            setOnClickListener { onContinue(s) }
        }
        val btnDelete = TextView(holder.itemView.context).apply {
            text = "删除"; setPadding(24, 12, 24, 12)
            setTextColor(0xFFFF5C5C.toInt()); setBackgroundColor(0xFF1A2332.toInt())
        }
        actions.addView(btnContinue); actions.addView(btnDelete)
    }
}
