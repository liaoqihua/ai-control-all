package com.example.aicontrolall.ui

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aicontrolall.R
import com.example.aicontrolall.util.ConfigManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var configMgr: ConfigManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        configMgr = ConfigManager(this)

        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val etModel = findViewById<EditText>(R.id.etModel)
        val etBaseUrl = findViewById<EditText>(R.id.etBaseUrl)
        val swEvolution = findViewById<Switch>(R.id.swEvolution)
        val tvConfigPath = findViewById<TextView>(R.id.tvConfigPath)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        etApiKey.setText(configMgr.apiKey)
        etModel.setText(configMgr.model)
        etBaseUrl.setText(configMgr.baseUrl)
        swEvolution.isChecked = configMgr.evolutionEnabled
        tvConfigPath.text = "配置文件: ${configMgr.getConfigFilePath()}"

        btnSave.setOnClickListener {
            configMgr.apiKey = etApiKey.text.toString().trim()
            configMgr.model = etModel.text.toString().trim()
            configMgr.baseUrl = etBaseUrl.text.toString().trim()
            configMgr.evolutionEnabled = swEvolution.isChecked

            Toast.makeText(this, "✅ 设置已保存到本地文件", Toast.LENGTH_SHORT).show()
        }

        btnBack.setOnClickListener {
            finish()
        }
    }
}
