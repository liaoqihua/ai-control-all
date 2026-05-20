package com.example.aicontrolall.ui

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aicontrolall.R
import com.example.aicontrolall.core.AgentConfig
import com.example.aicontrolall.core.AgentCore
import com.example.aicontrolall.core.PromptBuilder
import com.example.aicontrolall.evolution.EvolutionCycle
import com.example.aicontrolall.llm.LlmClient
import com.example.aicontrolall.mcp.McpGateway
import com.example.aicontrolall.mcp.tools.CameraTool
import com.example.aicontrolall.mcp.tools.SearchMemoriesTool
import com.example.aicontrolall.mcp.tools.SpeechTool
import com.example.aicontrolall.memory.DatabaseHelper
import com.example.aicontrolall.memory.MemoryStore
import com.example.aicontrolall.memory.SessionStore
import com.example.aicontrolall.memory.SkillStore
import com.example.aicontrolall.util.ConfigManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var agentCore: AgentCore
    private lateinit var memoryStore: MemoryStore
    private lateinit var skillStore: SkillStore
    private lateinit var sessionStore: SessionStore
    private lateinit var mcpGateway: McpGateway
    private lateinit var configMgr: ConfigManager
    private lateinit var speechTool: SpeechTool
    private lateinit var chatAdapter: ChatAdapter
    private var sessionId: String = ""

    private lateinit var etInput: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnHamburger: TextView
    private lateinit var tvStatusPill: TextView
    private lateinit var rvChat: RecyclerView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var menuDrawer: android.view.View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)
        btnHamburger = findViewById(R.id.btnHamburger)
        tvStatusPill = findViewById(R.id.tvStatusPill)
        rvChat = findViewById(R.id.rvChat)
        drawerLayout = findViewById(R.id.drawerLayout)
        menuDrawer = findViewById(R.id.menuDrawer)

        chatAdapter = ChatAdapter()
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = chatAdapter

        rvChat.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                currentFocus?.clearFocus()
                rvChat.clearFocus()
            }
            false
        }

        initializeAgent()

        btnSend.setOnClickListener { sendMessage() }
        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }

        // Hamburger toggles the menu drawer
        btnHamburger.setOnClickListener {
            if (drawerLayout.isDrawerOpen(menuDrawer)) {
                drawerLayout.closeDrawer(menuDrawer)
            } else {
                drawerLayout.openDrawer(menuDrawer)
            }
        }

        // Long-press hamburger → settings
        btnHamburger.setOnLongClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
    }

    private fun sendMessage() {
        val input = etInput.text.toString().trim()
        if (input.isBlank()) return

        etInput.text.clear()
        chatAdapter.addMessage(ChatMessage(text = input, isUser = true))
        rvChat.scrollToPosition(chatAdapter.itemCount - 1)
        processUserInput(input)
    }

    private fun initializeAgent() {
        val dbHelper = DatabaseHelper(this)
        memoryStore = MemoryStore(dbHelper)
        skillStore = SkillStore(dbHelper)
        sessionStore = SessionStore(dbHelper)
        configMgr = ConfigManager(this)

        mcpGateway = McpGateway()
        speechTool = SpeechTool(this)
        mcpGateway.register(speechTool)
        mcpGateway.register(CameraTool(this))
        mcpGateway.register(SearchMemoriesTool(memoryStore, sessionStore))

        val config = AgentConfig.fromConfigManager(configMgr)
        val llmClient = LlmClient(configMgr)
        val promptBuilder = PromptBuilder(memoryStore, skillStore, sessionStore, mcpGateway)
        val evolutionCycle = EvolutionCycle(memoryStore, skillStore, sessionStore)

        agentCore = AgentCore(
            config = config,
            memoryStore = memoryStore,
            skillStore = skillStore,
            sessionStore = sessionStore,
            mcpGateway = mcpGateway,
            llmClient = llmClient,
            promptBuilder = promptBuilder,
            evolutionCycle = evolutionCycle
        )

        sessionId = sessionStore.createSession()
        updateStatusBar()
    }

    private fun processUserInput(input: String) {
        lifecycleScope.launch {
            try {
                val result = agentCore.processInput(input, sessionId)
                chatAdapter.addMessage(ChatMessage(text = result.reply, isUser = false))

                if (result.toolResults.isNotEmpty()) {
                    val toolsSummary = result.toolResults.joinToString("\n") {
                        "${if (it.success) "✓" else "✗"} ${it.toolName}"
                    }
                    chatAdapter.addMessage(ChatMessage(text = "🔧 $toolsSummary", isUser = false))
                }

                rvChat.scrollToPosition(chatAdapter.itemCount - 1)
                updateStatusBar()
            } catch (e: Exception) {
                chatAdapter.addMessage(ChatMessage(
                    text = "⚠ ${e.message}",
                    isUser = false
                ))
            }
        }
    }

    private fun updateStatusBar() {
        val status = agentCore.getStatus()
        val mem = Regex("Memories: (\\d+)").find(status)?.groupValues?.get(1) ?: "0"
        val sk = Regex("Skills: (\\d+)").find(status)?.groupValues?.get(1) ?: "0"
        val tools = Regex("Tools: (\\d+)").find(status)?.groupValues?.get(1) ?: "0"
        val drills = Regex("Drills: (\\d+)").find(status)?.groupValues?.get(1) ?: "0"
        tvStatusPill.text = "● M:$mem S:$sk T:$tools D:$drills"
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(menuDrawer)) {
            drawerLayout.closeDrawer(menuDrawer)
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechTool.shutdown()
        agentCore.shutdown()
    }
}
