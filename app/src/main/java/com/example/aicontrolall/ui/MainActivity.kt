package com.example.aicontrolall.ui

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var btnMenu: TextView
    private lateinit var btnClosePanel: TextView
    private lateinit var tvStatus: TextView
    private lateinit var rvChat: RecyclerView
    private lateinit var statusPanel: View

    // Panel detail views
    private lateinit var tvPanelStats: TextView
    private lateinit var tvPanelMemories: TextView
    private lateinit var tvPanelTools: TextView
    private lateinit var tvPanelSkills: TextView
    private lateinit var tvPanelConfig: TextView

    private var panelOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)
        btnMenu = findViewById(R.id.btnMenu)
        btnClosePanel = findViewById(R.id.btnClosePanel)
        tvStatus = findViewById(R.id.tvStatus)
        rvChat = findViewById(R.id.rvChat)
        statusPanel = findViewById(R.id.statusPanel)

        // Panel detail views
        tvPanelStats = findViewById(R.id.tvPanelStats)
        tvPanelMemories = findViewById(R.id.tvPanelMemories)
        tvPanelTools = findViewById(R.id.tvPanelTools)
        tvPanelSkills = findViewById(R.id.tvPanelSkills)
        tvPanelConfig = findViewById(R.id.tvPanelConfig)

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

        // Hamburger menu → slide panel
        btnMenu.setOnClickListener {
            if (panelOpen) closePanel() else openPanel()
        }

        btnClosePanel.setOnClickListener { closePanel() }

        // Long-press hamburger → settings
        btnMenu.setOnLongClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
    }

    private fun openPanel() {
        refreshPanelData()
        statusPanel.animate()
            .translationX(0f)
            .setDuration(250)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        panelOpen = true
    }

    private fun closePanel() {
        statusPanel.animate()
            .translationX(statusPanel.width.toFloat())
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        panelOpen = false
    }

    private fun refreshPanelData() {
        // Core stats
        val status = agentCore.getStatus()
        val mem = Regex("Memories: (\\d+)").find(status)?.groupValues?.get(1) ?: "0"
        val sk = Regex("Skills: (\\d+)").find(status)?.groupValues?.get(1) ?: "0"
        val tools = Regex("Tools: (\\d+)").find(status)?.groupValues?.get(1) ?: "0"
        val model = Regex("Model: (.+)").find(status)?.groupValues?.get(1) ?: configMgr.model
        val evo = Regex("Evolution: (.+)").find(status)?.groupValues?.get(1) ?: "enabled"

        tvPanelStats.text = """
            Memories       $mem
            Skills         $sk
            Tools          $tools
            Model          $model
            Evolution      $evo
        """.trimIndent()

        // Recent memories
        val memories = memoryStore.getRecent(5)
        tvPanelMemories.text = if (memories.isEmpty()) "(none)" else {
            memories.joinToString("\n") { "• ${it.content.take(60)}" }
        }

        // Available tools
        val toolList = mcpGateway.listTools()
        tvPanelTools.text = if (toolList.isEmpty()) "(none)" else {
            toolList.joinToString("\n") { "▸ ${it.name}" }
        }

        // Active skills
        val skills = skillStore.getAll(10)
        tvPanelSkills.text = if (skills.isEmpty()) "(none)" else {
            skills.joinToString("\n") { "▸ ${it.title} (${(it.confidence * 100).toInt()}%)" }
        }

        // Config
        tvPanelConfig.text = "Path: ${configMgr.getConfigFilePath()}"
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
                if (panelOpen) refreshPanelData()
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
        tvStatus.text = "M:$mem S:$sk T:$tools"
    }

    override fun onBackPressed() {
        if (panelOpen) {
            closePanel()
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
