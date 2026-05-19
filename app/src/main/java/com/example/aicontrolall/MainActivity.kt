package com.example.aicontrolall

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.aicontrolall.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvGreeting.text = getString(R.string.hello_world)

        binding.btnClickMe.setOnClickListener {
            binding.tvGreeting.text = getString(R.string.button_clicked)
        }
    }
}
