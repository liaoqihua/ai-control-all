package com.example.aicontrolall.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aicontrolall.R

class MenuAdapter(
    private val items: List<MenuItem>,
    private val onItemClick: (MenuItem) -> Unit
) : RecyclerView.Adapter<MenuAdapter.ViewHolder>() {

    private val badgeCounts = mutableMapOf<Int, Int>()

    inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val iconView = holder.itemView.findViewById<TextView>(R.id.tvIcon)
        val labelView = holder.itemView.findViewById<TextView>(R.id.tvLabel)
        val badgeView = holder.itemView.findViewById<TextView>(R.id.tvBadge)

        iconView.text = item.icon
        labelView.text = item.label
        val badge = badgeCounts[position] ?: item.badge
        if (badge > 0) {
            badgeView.text = badge.toString()
            badgeView.visibility = android.view.View.VISIBLE
        } else {
            badgeView.visibility = android.view.View.GONE
        }
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateBadge(position: Int, count: Int) {
        if (position in items.indices) {
            badgeCounts[position] = count
            notifyItemChanged(position)
        }
    }
}
