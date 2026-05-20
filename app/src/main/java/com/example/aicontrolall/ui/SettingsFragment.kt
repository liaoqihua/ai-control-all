package com.example.aicontrolall.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.aicontrolall.R
import com.example.aicontrolall.util.ConfigManager

class SettingsFragment : Fragment() {
    private var configMgr: ConfigManager? = null
    private var evolutionEnabled = true

    fun setConfigManager(cm: ConfigManager) { configMgr = cm }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        val etApiKey = view.findViewById<EditText>(R.id.etApiKey)
        val etModel = view.findViewById<EditText>(R.id.etModel)
        val etBaseUrl = view.findViewById<EditText>(R.id.etBaseUrl)
        val tvEvo = view.findViewById<TextView>(R.id.tvEvolutionStatus)
        val tvVersion = view.findViewById<TextView>(R.id.tvVersion)

        view.findViewById<TextView>(R.id.tvBack).setOnClickListener {
            (requireActivity() as? MainActivity)?.navigateBack()
        }

        configMgr?.let { cm ->
            etApiKey.setText(cm.apiKey)
            etModel.setText(cm.model)
            etBaseUrl.setText(cm.baseUrl)
            evolutionEnabled = cm.evolutionEnabled
        }
        updateEvoTag(tvEvo)
        tvVersion.text = "v0.2.0-dev"

        // Immediate toggle on tap
        view.findViewById<View>(R.id.rowEvolution).setOnClickListener {
            evolutionEnabled = !evolutionEnabled
            configMgr?.evolutionEnabled = evolutionEnabled
            updateEvoTag(tvEvo)
        }

        // Save on focus loss
        val saveConfig = {
            configMgr?.let { cm ->
                cm.apiKey = etApiKey.text.toString().trim()
                cm.model = etModel.text.toString().trim()
                cm.baseUrl = etBaseUrl.text.toString().trim()
                Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
            }
        }
        etApiKey.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveConfig() }
        etModel.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveConfig() }
        etBaseUrl.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveConfig() }

        view.findViewById<View>(R.id.btnReset).setOnClickListener {
            Toast.makeText(requireContext(), "重置功能待实现", Toast.LENGTH_SHORT).show()
        }
        return view
    }

    private fun updateEvoTag(tv: TextView) {
        if (evolutionEnabled) {
            tv.text = "已开启"; tv.setTextColor(0xFF06D6A0.toInt())
        } else {
            tv.text = "已关闭"; tv.setTextColor(0xFFFF5C5C.toInt())
        }
    }
}
