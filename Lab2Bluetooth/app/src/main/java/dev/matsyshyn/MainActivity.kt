package dev.matsyshyn

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val cardClient = findViewById<MaterialCardView>(R.id.cardClient)
        val cardServer = findViewById<MaterialCardView>(R.id.cardServer)

        // Анімація появи карток
        animateCard(cardClient, 0)
        animateCard(cardServer, 150)

        // Обробники кліків по картках
        cardClient.setOnClickListener {
            animateClick(cardClient) {
                startActivity(Intent(this, ClientActivity::class.java))
            }
        }

        cardServer.setOnClickListener {
            animateClick(cardServer) {
                startActivity(Intent(this, ServerActivity::class.java))
            }
        }
    }

    private fun animateCard(card: MaterialCardView, delay: Long) {
        card.alpha = 0f
        card.translationY = 50f
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(delay)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun animateClick(view: View, action: () -> Unit) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f, 1f)
        
        scaleX.duration = 200
        scaleY.duration = 200
        
        scaleX.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                action()
            }
        })
        
        scaleX.start()
        scaleY.start()
    }
}