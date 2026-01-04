package dev.matsyshyn.lab5ml

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnServerMode: Button = findViewById(R.id.btnServerMode)
        val btnCollectorMode: Button = findViewById(R.id.btnCollectorMode)

        btnServerMode.setOnClickListener {
            startActivity(Intent(this, ServerActivity::class.java))
        }

        btnCollectorMode.setOnClickListener {
            startActivity(Intent(this, ClientActivity::class.java))
        }
    }
}