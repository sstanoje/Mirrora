package com.s2d2.mirrora.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.Selection
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.widget.AppCompatButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.s2d2.mirrora.R
import com.s2d2.mirrora.connection.Room
import com.s2d2.mirrora.connection.WsSignalingClient
import com.s2d2.mirrora.ui.dpScaled
import com.s2d2.mirrora.ui.spFloatFromDpText
import com.s2d2.mirrora.ui.spFromDpText
import com.s2d2.mirrora.utils.BlockingLoaderDialog
import com.s2d2.mirrora.utils.WebUtils
import org.webrtc.IceCandidate

class JoinScreenActivityCompose : ComponentActivity() {

    companion object { private const val EXTRA_ROOM = "ROOM" }

    private val isConnecting = mutableStateOf(false)
    private val connectingText = mutableStateOf("Connecting…")

    private var wsProbe: WsSignalingClient? = null
    private val main = Handler(Looper.getMainLooper())
    private var probeCompleted = false
    private val PROBE_TIMEOUT_MS = 10_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val ctx = LocalContext.current

            val widthDp = LocalConfiguration.current.screenWidthDp
            val scale = when {
                widthDp >= 840 -> 1.35f
                widthDp >= 600 -> 1.18f
                else -> 1f
            }

            val bg = Brush.verticalGradient(listOf(Color(0xFFB0E1FC), Color(0xFFD6F3FC)))

            var code by remember { mutableStateOf("") }

            val TITLE_TOP = 100f
            val DESC_BOTTOM = 125f
            val CODE_BOTTOM = 30f
            val BTN_HEIGHT = 70f
            val BTN_BOTTOM = 20f
            val SIDE_20 = 20f

            val TITLE_TXT_DP = 40f
            val DESC_TXT_DP = 20f
            val CODE_TXT_DP = 34f
            val BTN_TXT_DP = 20f

            MaterialTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(bg)
                        .statusBarsPadding()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(dpScaled(TITLE_TOP, scale)))
                        Text(
                            text = "Mirrora",
                            fontSize = spFromDpText(ctx, TITLE_TXT_DP) * scale,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C6FBB),
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Enter the code to join",
                            fontSize = spFromDpText(ctx, DESC_TXT_DP) * scale,
                            color = Color(0xFF3691F0),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(dpScaled(DESC_BOTTOM, scale)))

                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = dpScaled(SIDE_20, scale),
                                    end = dpScaled(SIDE_20, scale),
                                    bottom = dpScaled(CODE_BOTTOM, scale)
                                ),
                            factory = { context ->
                                EditText(context).apply {
                                    background = ContextCompat.getDrawable(
                                        context,
                                        R.drawable.edit_text_background
                                    )
                                    setHint("000000")
                                    setHintTextColor(Color(0xFF6B7280).toArgb())
                                    setTextColor(
                                        ContextCompat.getColor(
                                            context,
                                            R.color.colorPrimary
                                        )
                                    )
                                    setTextSize(
                                        TypedValue.COMPLEX_UNIT_SP,
                                        spFloatFromDpText(context, CODE_TXT_DP) * scale
                                    )
                                    setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                                    letterSpacing = 0.35f
                                    includeFontPadding = false
                                    inputType = InputType.TYPE_CLASS_NUMBER
                                    keyListener = DigitsKeyListener.getInstance("0123456789")
                                    filters = arrayOf(InputFilter.LengthFilter(6))
                                    gravity = Gravity.CENTER
                                    setOnFocusChangeListener { v, hasFocus ->
                                        val e = v as EditText
                                        e.isCursorVisible = hasFocus
                                        if (!hasFocus) {
                                            Selection.removeSelection(e.text)
                                            val imm =
                                                context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                                            imm.hideSoftInputFromWindow(v.windowToken, 0)
                                        }
                                    }
                                }
                            },
                            update = { et ->
                                if (et.text.toString() != code) {
                                    et.setText(code)
                                    et.setSelection(code.length)
                                }
                                val root = window.decorView.findViewById<View>(android.R.id.content)
                                ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
                                    val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                                    et.isCursorVisible = imeVisible && et.hasFocus()
                                    insets
                                }
                                et.addTextChangedListener(object : TextWatcher {
                                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                        val v = s?.toString().orEmpty().take(6)
                                        if (v != code) code = v
                                    }
                                    override fun afterTextChanged(s: Editable?) {}
                                })
                            }
                        )

                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(
                                    start = dpScaled(SIDE_20, scale),
                                    end = dpScaled(SIDE_20, scale),
                                    bottom = dpScaled(BTN_BOTTOM, scale)
                                )
                                .height(dpScaled(BTN_HEIGHT, scale)),
                            factory = { context ->
                                AppCompatButton(context).apply {
                                    background = ContextCompat.getDrawable(
                                        context,
                                        R.drawable.rounded_button
                                    )
                                    isAllCaps = false
                                    setTextColor(Color.White.toArgb())
                                    text = "Join session"
                                    setTextSize(
                                        TypedValue.COMPLEX_UNIT_SP,
                                        spFloatFromDpText(context, BTN_TXT_DP) * scale
                                    )
                                    setTypeface(typeface, Typeface.BOLD)
                                    gravity = Gravity.CENTER
                                    includeFontPadding = false
                                    setPaddingRelative(0, 0, 0, 0)
                                    setPadding(0, 0, 0, 0)
                                    minHeight = 0; minimumHeight = 0
                                    minWidth = 0; minimumWidth = 0

                                    setOnClickListener {
                                        if (isRoomCodeValid(code)) {
                                            // Show blocking loader and start probe
                                            isConnecting.value = true
                                            connectingText.value = "Connecting"
                                            startProbeThenNavigate(code)
                                        }
                                    }
                                }
                            }
                        )
                    }

//                    BlockingLoaderDialog(
//                        visible = isConnecting.value,
//                        message = connectingText.value
//                    )
                }
            }

            BackHandler {
                if (isConnecting.value) {
                    Toast.makeText(this@JoinScreenActivityCompose, "Back pressed.", Toast.LENGTH_SHORT).show()
                    cleanupProbe()
                    isConnecting.value = false
                } else {
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupProbe()
    }

    private fun isRoomCodeValid(text: String): Boolean {
        val roomCode = text.trim()
        if (roomCode.isEmpty()) {
            Toast.makeText(this, "You must enter a room code", Toast.LENGTH_SHORT).show()
            return false
        }
        if (roomCode.length != 6) {
            Toast.makeText(this, "Room code must contain exactly six numbers", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun startProbeThenNavigate(roomCode: String) {
        probeCompleted = false

        wsProbe = WsSignalingClient(
            WebUtils.SERVER_IP_ADDRESS,
            roomCode,
            object : WsSignalingClient.Listener {
                override fun onOpen() {
                    connectingText.value = "Connected"
                }

                override fun onJoined(roomCode: String, peers: Int) {
                    if (probeCompleted) return
                    probeCompleted = true
                    main.removeCallbacksAndMessages(null)
                    goToViewer(roomCode)
                }

                override fun onReady(room: String) { /* no-op for probe */ }

                override fun onAnswer(r: String, sdp: String) { /* no-op */ }

                override fun onOffer(room: String, sdp: String) { /* no-op */ }

                override fun onIce(c: IceCandidate) { /* no-op */ }

                override fun onPeerLeft() { /* no-op */ }

                override fun onError(reason: String) {
                    if (probeCompleted) return
                    probeCompleted = true
                    main.removeCallbacksAndMessages(null)
                    isConnecting.value = false
                    Toast.makeText(this@JoinScreenActivityCompose, reason, Toast.LENGTH_SHORT).show()
                    cleanupProbe()
                }

                override fun onClosed() {
                    if (probeCompleted) return
                    probeCompleted = true
                    main.removeCallbacksAndMessages(null)
                    isConnecting.value = false
//                    Toast.makeText(this@JoinScreenActivityCompose, "Connection closed.", Toast.LENGTH_SHORT).show()
                    cleanupProbe()
                }
            },
            WsSignalingClient.Role.VIEWER
        )

        wsProbe?.connect()

        // Timeout to avoid hanging forever
        main.postDelayed({
            if (!probeCompleted) {
                probeCompleted = true
                isConnecting.value = false
                Toast.makeText(this, "Connection timed out.", Toast.LENGTH_SHORT).show()
                cleanupProbe()
            }
        }, PROBE_TIMEOUT_MS)
    }

    private fun goToViewer(roomCode: String) {
        cleanupProbe()
        val room = Room(roomCode)
        val i = Intent(this, RTCViewerActivityCompose::class.java)
        i.putExtra(EXTRA_ROOM, room)
        startActivity(i)
        finish()
    }

    private fun cleanupProbe() {
        try { wsProbe?.close() } catch (_: Throwable) {}
        wsProbe = null
    }
}
