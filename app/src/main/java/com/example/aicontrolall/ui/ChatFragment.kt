package com.example.aicontrolall.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aicontrolall.R
import com.example.aicontrolall.core.AgentCore
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {
    private var agentCore: AgentCore? = null
    private var sessionId: String = ""
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var rvChat: RecyclerView
    private lateinit var etInput: EditText

    fun setAgentCore(core: AgentCore, sid: String) {
        agentCore = core; sessionId = sid
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_chat, container, false)
        rvChat = view.findViewById(R.id.rvChat)
        etInput = view.findViewById(R.id.etInput)
        val btnSend = view.findViewById<ImageButton>(R.id.btnSend)

        chatAdapter = ChatAdapter()
        rvChat.layoutManager = LinearLayoutManager(requireContext())
        rvChat.adapter = chatAdapter

        btnSend.setOnClickListener { sendMessage() }
        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }
        return view
    }

    private fun sendMessage() {
        val input = etInput.text.toString().trim()
        if (input.isBlank() || agentCore == null) return
        etInput.text.clear()
        chatAdapter.addMessage(ChatMessage(text = input, isUser = true))
        rvChat.scrollToPosition(chatAdapter.itemCount - 1)
        lifecycleScope.launch {
            try {
                val result = agentCore!!.processInput(input, sessionId)
                chatAdapter.addMessage(ChatMessage(text = result.reply, isUser = false))
                if (result.toolResults.isNotEmpty()) {
                    val toolsSummary = result.toolResults.joinToString("\n") { "${if (it.success) "✓" else "✗"} ${it.toolName}" }
                    chatAdapter.addMessage(ChatMessage(text = "🔧 $toolsSummary", isUser = false))
                }
                rvChat.scrollToPosition(chatAdapter.itemCount - 1)
            } catch (e: Exception) {
                chatAdapter.addMessage(ChatMessage(text = "⚠ ${e.message}", isUser = false))
            }
        }
    }
}
