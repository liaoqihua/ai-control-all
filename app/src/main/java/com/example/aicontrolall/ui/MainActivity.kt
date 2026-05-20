package com.example.aicontrolall.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
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

class MainActivity : AppCompatActivity() {

    private lateinit var agentCore: AgentCore
    private lateinit var memoryStore: MemoryStore
    private lateinit var skillStore: SkillStore
    private lateinit var sessionStore: SessionStore
    private lateinit var mcpGateway: McpGateway
    private lateinit var configMgr: ConfigManager
    private lateinit var speechTool: SpeechTool
    private var sessionId: String = ""

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var rvMenu: RecyclerView
    private lateinit var btnHamburger: TextView
    private lateinit var tvStatusPill: TextView
    private lateinit var menuAdapter: MenuAdapter

    private val menuItems = listOf(
        MenuItem("chat", "\uD83D\uDCAC", "聊天"),
        MenuItem("history", "\uD83D\uDD51", "历史会话"),
        MenuItem("memory", "\u25C6", "记忆"),
        MenuItem("tools", "\u2692", "工具"),
        MenuItem("skills", "\u2605", "技能"),
        MenuItem("divider", "", "", isDivider = true),
        MenuItem("devices", "\uD83D\uDD27", "设备"),
        MenuItem("settings", "\u2699", "设置")
    )

    private val fragments = mutableMapOf<String, Fragment>()
    private var currentPage = "default"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnHamburger = findViewById(R.id.btnHamburger)
        tvStatusPill = findViewById(R.id.tvStatusPill)
        drawerLayout = findViewById(R.id.drawerLayout)
        rvMenu = findViewById(R.id.rvMenu)

        // Setup menu
        menuAdapter = MenuAdapter(menuItems.filter { !it.isDivider }) { item ->
            showPage(item.id)
            drawerLayout.closeDrawer(GravityCompat.END)
        }
        rvMenu.layoutManager = LinearLayoutManager(this)
        rvMenu.adapter = menuAdapter

        // Hamburger toggles drawer
        btnHamburger.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.END))
                drawerLayout.closeDrawer(GravityCompat.END)
            else
                drawerLayout.openDrawer(GravityCompat.END)
        }

        // Initialize agent
        initializeAgent()

        // Show default page
        showPage("default")
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
        updateStatusPill()
    }

    fun showPage(pageId: String) {
        currentPage = pageId
        val fragment = fragments.getOrPut(pageId) {
            when (pageId) {
                "default" -> DefaultPageFragment()
                "chat" -> ChatFragment().also {
                    it.setAgentCore(agentCore, sessionId)
                }
                "history" -> HistoryFragment().also {
                    it.setSessionStore(sessionStore)
                }
                "memory" -> MemoryFragment().also {
                    it.setMemoryStore(memoryStore)
                }
                "tools" -> ToolsFragment().also {
                    it.setMcpGateway(mcpGateway)
                }
                "skills" -> SkillsFragment().also {
                    it.setSkillStore(skillStore)
                }
                "devices" -> DevicesFragment()
                "settings" -> SettingsFragment().also {
                    it.setConfigManager(configMgr)
                }
                else -> DefaultPageFragment()
            }
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.contentFrame, fragment)
            .addToBackStack(null)
            .commit()

        updateStatusPill()
    }

    fun navigateBack() {
        if (currentPage != "default") {
            showPage("default")
        }
    }

    fun updateStatusPill() {
        val m = memoryStore.count()
        val s = skillStore.getAll(100).size
        val t = mcpGateway.listTools().size
        val d = 8
        tvStatusPill.text = "\u25CF M:$m S:$s T:$t D:$d"
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END)
        } else if (currentPage != "default") {
            showPage("default")
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
