package com.example.aichat.ui.voice

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import androidx.annotation.RawRes
import com.example.aichat.R
import com.example.aichat.ui.chat.ChatViewModel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import timber.log.Timber

class VoiceOrbState {
    private val internal = AtomicReference(Triple(0f, 0f, ChatViewModel.VoiceState.Idle))

    fun update(amplitude: Float, centroid: Float, state: ChatViewModel.VoiceState) {
        val normalizedAmp = amplitude.coerceIn(0f, 1f)
        val normalizedCentroid = (centroid / 4000f).coerceIn(0f, 1f)
        internal.set(Triple(normalizedAmp, normalizedCentroid, state))
    }

    fun snapshot(): Triple<Float, Float, ChatViewModel.VoiceState> = internal.get()
}

class VoiceOrbGLView(context: Context) : GLSurfaceView(context) {
    private var orbRenderer: VoiceOrbRenderer? = null
    private var onRendererError: (() -> Unit)? = null

    init {
        setEGLContextClientVersion(2)
    }

    fun attachRenderer(renderer: VoiceOrbRenderer) {
        orbRenderer = renderer
        renderer.onError = onRendererError
        setRenderer(renderer)
    }

    fun updateState(state: VoiceOrbState) {
        orbRenderer?.state = state
    }

    fun setOnRendererError(block: () -> Unit) {
        onRendererError = block
        orbRenderer?.onError = block
    }
}

class VoiceOrbRenderer(
    private val context: Context,
    var state: VoiceOrbState
) : GLSurfaceView.Renderer {

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private lateinit var mesh: SphereMesh

    private var program = 0
    private var timeStart = System.nanoTime()

    var onError: (() -> Unit)? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            mesh = SphereMesh(48, 48)
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, loadRawText(R.raw.orb_vertex))
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, loadRawText(R.raw.orb_fragment))
            program = GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, vertexShader)
                GLES20.glAttachShader(it, fragmentShader)
                GLES20.glLinkProgram(it)
            }
            Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 2.6f, 0f, 0f, 0f, 0f, 1f, 0f)
        } catch (ex: Exception) {
            Timber.e(ex, "Renderer init failed")
            onError?.invoke()
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 2f, 7f)
    }

    override fun onDrawFrame(gl: GL10?) {
        try {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            GLES20.glUseProgram(program)

            val timeSeconds = (System.nanoTime() - timeStart) / 1_000_000_000f
            val (amp, centroid, voiceState) = state.snapshot()
            val modeValue = when (voiceState) {
                ChatViewModel.VoiceState.Idle -> 0f
                ChatViewModel.VoiceState.Listening -> 1f
                ChatViewModel.VoiceState.Thinking -> 2f
                ChatViewModel.VoiceState.Speaking -> 3f
            }

            val mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
            val timeHandle = GLES20.glGetUniformLocation(program, "uTime")
            val ampHandle = GLES20.glGetUniformLocation(program, "uAmplitude")
            val centroidHandle = GLES20.glGetUniformLocation(program, "uCentroid")
            val modeHandle = GLES20.glGetUniformLocation(program, "uMode")
            val colorAHandle = GLES20.glGetUniformLocation(program, "uColorA")
            val colorBHandle = GLES20.glGetUniformLocation(program, "uColorB")

            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniform1f(timeHandle, timeSeconds)
            GLES20.glUniform1f(ampHandle, amp)
            GLES20.glUniform1f(centroidHandle, centroid)
            GLES20.glUniform1f(modeHandle, modeValue)
            GLES20.glUniform3f(colorAHandle, 0.36f, 0.42f, 1.0f)
            GLES20.glUniform3f(colorBHandle, 1.0f, 0.31f, 0.85f)

            mesh.bind(program)
            mesh.draw()
        } catch (ex: Exception) {
            Timber.e(ex, "Renderer draw failed")
            onError?.invoke()
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                val info = GLES20.glGetShaderInfoLog(shader)
                Timber.e("Shader compile error: $info")
                GLES20.glDeleteShader(shader)
                throw IllegalStateException("Shader compile failed")
            }
        }
    }

    private fun loadRawText(@RawRes resId: Int): String {
        val inputStream = context.resources.openRawResource(resId)
        val reader = BufferedReader(InputStreamReader(inputStream))
        return buildString {
            reader.forEachLine { appendLine(it) }
        }
    }
}

private class SphereMesh(stacks: Int, slices: Int) {
    private val vertexBuffer: FloatBuffer
    private val normalBuffer: FloatBuffer
    private val indexBuffer: ShortBuffer
    private val indexCount: Int

    init {
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val indices = mutableListOf<Short>()

        for (stack in 0..stacks) {
            val phi = Math.PI * stack / stacks
            val sinPhi = Math.sin(phi).toFloat()
            val cosPhi = Math.cos(phi).toFloat()

            for (slice in 0..slices) {
                val theta = 2.0 * Math.PI * slice / slices
                val sinTheta = Math.sin(theta).toFloat()
                val cosTheta = Math.cos(theta).toFloat()

                val x = cosTheta * sinPhi
                val y = cosPhi
                val z = sinTheta * sinPhi

                vertices.add(x)
                vertices.add(y)
                vertices.add(z)

                normals.add(x)
                normals.add(y)
                normals.add(z)
            }
        }

        for (stack in 0 until stacks) {
            for (slice in 0 until slices) {
                val first = (stack * (slices + 1) + slice).toShort()
                val second = (first + slices + 1).toShort()

                indices.add(first)
                indices.add(second)
                indices.add((first + 1).toShort())

                indices.add(second)
                indices.add((second + 1).toShort())
                indices.add((first + 1).toShort())
            }
        }

        vertexBuffer = floatBuffer(vertices.toFloatArray())
        normalBuffer = floatBuffer(normals.toFloatArray())
        indexBuffer = shortBuffer(indices.toShortArray())
        indexCount = indices.size
    }

    fun bind(program: Int) {
        val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        val normalHandle = GLES20.glGetAttribLocation(program, "aNormal")

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, normalBuffer)
    }

    fun draw() {
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indexBuffer)
    }

    private fun floatBuffer(array: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(array.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(array)
            position(0)
        }

    private fun shortBuffer(array: ShortArray): ShortBuffer =
        ByteBuffer.allocateDirect(array.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply {
            put(array)
            position(0)
        }
}
