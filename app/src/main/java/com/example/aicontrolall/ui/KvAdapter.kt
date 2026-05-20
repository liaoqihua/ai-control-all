package com.example.aicontrolall.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aicontrolall.R

class KvAdapter(private val items: List<Pair<String, String>>) : RecyclerView.Adapter<KvAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvKey: TextView = view.findViewById(R.id.tvKey)
        val tvVal: TextView = view.findViewById(R.id.tvVal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_kv, parent, false)
        return VH(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val (key, value) = items[pos]
        holder.tvKey.text = key
        holder.tvVal.text = value
    }
}
