package dev.matsyshyn.lab5ml

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2

class ClientActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client)

        val viewPager: ViewPager2 = findViewById(R.id.viewPager)
        val tabLayout: com.google.android.material.tabs.TabLayout = findViewById(R.id.tabLayout)

        val adapter = ClientPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Collector"
                1 -> "Prediction"
                else -> ""
            }
        }.attach()
    }
}

