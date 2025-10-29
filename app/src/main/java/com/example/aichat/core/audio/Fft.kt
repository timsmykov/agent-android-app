package com.example.aichat.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal object Fft {
    fun forward(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        require(n == imag.size) { "Real and imaginary arrays must be the same size" }
        require(n != 0 && n and (n - 1) == 0) { "Size must be power of two" }

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tempReal = real[i]
                real[i] = real[j]
                real[j] = tempReal

                val tempImag = imag[i]
                imag[i] = imag[j]
                imag[j] = tempImag
            }
        }

        var length = 2
        while (length <= n) {
            val half = length / 2
            val theta = -2.0 * PI / length
            val wCos = cos(theta)
            val wSin = sin(theta)
            for (i in 0 until n step length) {
                var wr = 1.0
                var wi = 0.0
                for (k in 0 until half) {
                    val evenIndex = i + k
                    val oddIndex = evenIndex + half

                    val oddReal = wr * real[oddIndex] - wi * imag[oddIndex]
                    val oddImag = wr * imag[oddIndex] + wi * real[oddIndex]

                    real[oddIndex] = real[evenIndex] - oddReal
                    imag[oddIndex] = imag[evenIndex] - oddImag

                    real[evenIndex] += oddReal
                    imag[evenIndex] += oddImag

                    val nextWr = wr * wCos - wi * wSin
                    wi = wr * wSin + wi * wCos
                    wr = nextWr
                }
            }
            length *= 2
        }
    }
}
