package dev.matsyshyn.smartparkingsystem.ui.monitoring

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import kotlin.math.max

class ThermometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val minTemp = -10f
    private val maxTemp = 40f
    private var currentTemp = 15f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }

    fun setTemperature(temp: Float) {
        currentTemp = temp.coerceIn(minTemp, maxTemp)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2f
        val bulbRadius = min(width, height) * 0.15f
        val tubeWidth = width * 0.2f
        val tubeHeight = height - bulbRadius * 2f
        val tubeTop = bulbRadius
        val tubeBottom = height - bulbRadius

        // Обчислюємо рівень температури (0.0 - 1.0)
        val tempRatio = (currentTemp - minTemp) / (maxTemp - minTemp)
        val fillHeight = tubeHeight * tempRatio
        val fillTop = tubeBottom - fillHeight

        // Визначаємо колір залежно від температури
        val color = when {
            currentTemp < 0f -> Color.parseColor("#2196F3") // Синій (холодно)
            currentTemp < 10f -> Color.parseColor("#4CAF50") // Зелений (прохолодно)
            currentTemp < 25f -> Color.parseColor("#FFC107") // Жовтий (нормально)
            else -> Color.parseColor("#FF5722") // Червоний (гаряче)
        }

        // Малюємо трубку
        val tubeRect = RectF(
            centerX - tubeWidth / 2f,
            tubeTop,
            centerX + tubeWidth / 2f,
            tubeBottom
        )
        paint.color = Color.parseColor("#E0E0E0")
        canvas.drawRoundRect(tubeRect, tubeWidth / 2f, tubeWidth / 2f, paint)

        // Малюємо заповнення (ртуть)
        if (fillHeight > 0) {
            val fillRect = RectF(
                centerX - tubeWidth / 2f + 4f,
                fillTop,
                centerX + tubeWidth / 2f - 4f,
                tubeBottom
            )
            paint.color = color
            canvas.drawRoundRect(fillRect, (tubeWidth - 8f) / 2f, (tubeWidth - 8f) / 2f, paint)
        }

        // Малюємо колбу внизу
        paint.color = color
        canvas.drawCircle(centerX, height - bulbRadius, bulbRadius, paint)

        // Малюємо значення температури
        canvas.drawText(
            "${currentTemp.toInt()}°C",
            centerX,
            height + textPaint.textSize,
            textPaint
        )
    }
}





