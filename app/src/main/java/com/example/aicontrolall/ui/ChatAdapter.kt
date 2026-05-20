package com.example.aicontrolall.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aicontrolall.R

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {
    private val messages = mutableListOf<ChatMessage>()

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_AI = 1
        private const val VIEW_TYPE_TOOL = 2
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: LinearLayout = view as LinearLayout
        val tvRole: TextView = view.findViewById(R.id.tvRole)
        val tvBubble: TextView = view.findViewById(R.id.tvBubble)
    }

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        return when {
            msg.text.startsWith("🔧") -> VIEW_TYPE_TOOL
            msg.isUser -> VIEW_TYPE_USER
            else -> VIEW_TYPE_AI
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = messages[position]
        val ctx = holder.itemView.context
        val density = ctx.resources.displayMetrics.density

        when (getItemViewType(position)) {
            VIEW_TYPE_USER -> {
                holder.tvRole.text = "YOU"
                holder.tvRole.gravity = Gravity.END
                holder.container.gravity = Gravity.END
                holder.tvBubble.text = msg.text
                holder.tvBubble.background = roundRect("#1E3A5F", "#0EA5E9", 16f * density, 1f * density)
                holder.tvBubble.setTextColor(Color.parseColor("#E2E8F0"))
            }
            VIEW_TYPE_AI -> {
                holder.tvRole.text = "AiControlAll"
                holder.tvRole.gravity = Gravity.START
                holder.container.gravity = Gravity.START
                holder.tvBubble.text = msg.text
                holder.tvBubble.background = roundRect("#1A2332", "#1E293B", 16f * density, 1f * density)
                holder.tvBubble.setTextColor(Color.parseColor("#E2E8F0"))
            }
            VIEW_TYPE_TOOL -> {
                holder.tvRole.text = "TOOL"
                holder.tvRole.gravity = Gravity.START
                holder.container.gravity = Gravity.START
                holder.tvBubble.text = msg.text
                holder.tvBubble.background = roundRect("#1A2E1A", "#22C55E", 16f * density, 0.5f * density)
                holder.tvBubble.setTextColor(Color.parseColor("#86EFAC"))
            }
        }
    }

    override fun getItemCount() = messages.size

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    private fun roundRect(fillHex: String, strokeHex: String, radius: Float, strokeWidth: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            cornerRadius = radius
            setStroke(strokeWidth.toInt(), Color.parseColor(strokeHex))
        }
    }
}
