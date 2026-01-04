package dev.matsyshyn.lab5ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ActivityClassifier(context: Context) {

    private var interpreter: Interpreter? = null
    
    // Розміри даних (як вчили модель)
    private val WINDOW_SIZE = 20  // 20 замірів
    private val NUM_SENSORS = 6   // ax, ay, az, gx, gy, gz
    private val NUM_CLASSES = 5   // Згинання, Підняття, Розведення, Обертання, Спокій
    private val FLOAT_SIZE = 4    // 4 байти на одне число float

    // Назви класів
    val labels = listOf("Згинання", "Підняття", "Розведення", "Обертання", "Спокій")

    init {
        try {
            val options = Interpreter.Options()
            interpreter = Interpreter(loadModelFile(context), options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Завантаження файлу з assets
    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // ГОЛОВНИЙ МЕТОД: Прогноз
    fun predict(dataWindow: List<FloatArray>): String {
        if (interpreter == null) return "Model Error"

        // 1. Підготовка вхідних даних (Input)
        // Модель чекає матрицю [1, 20, 6]
        val byteBuffer = ByteBuffer.allocateDirect(1 * WINDOW_SIZE * NUM_SENSORS * FLOAT_SIZE)
        byteBuffer.order(ByteOrder.nativeOrder())

        for (sample in dataWindow) {     // проходимо по 20 рядкам
            for (value in sample) {      // проходимо по 6 значенням
                byteBuffer.putFloat(value)
            }
        }

        // 2. Підготовка вихідних даних (Output)
        // Модель видасть 5 ймовірностей (наприклад: [0.1, 0.8, 0.05, ...])
        val output = Array(1) { FloatArray(NUM_CLASSES) }

        // 3. Запуск моделі
        interpreter?.run(byteBuffer, output)

        // 4. Пошук найкращого результату
        val probabilities = output[0]
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
        
        // Повертаємо назву класу + впевненість у %
        return if (maxIndex != -1) {
            val confidence = (probabilities[maxIndex] * 100).toInt()
            "${labels[maxIndex]} ($confidence%)"
        } else {
            "Unknown"
        }
    }
    
    fun getProbabilities(dataWindow: List<FloatArray>): FloatArray? {
        if (interpreter == null || dataWindow.size != WINDOW_SIZE) return null
        
        val byteBuffer = ByteBuffer.allocateDirect(1 * WINDOW_SIZE * NUM_SENSORS * FLOAT_SIZE)
        byteBuffer.order(ByteOrder.nativeOrder())

        for (sample in dataWindow) {
            for (value in sample) {
                byteBuffer.putFloat(value)
            }
        }

        val output = Array(1) { FloatArray(NUM_CLASSES) }
        interpreter?.run(byteBuffer, output)
        
        return output[0]
    }
}

