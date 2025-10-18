package com.s2d2.mirrora.ui

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
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
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.s2d2.mirrora.R
import com.s2d2.mirrora.ScreenShareService
import com.s2d2.mirrora.connection.Room
import com.s2d2.mirrora.connection.WsSignalingClient
import com.s2d2.mirrora.utils.BlockingLoaderDialog
import com.s2d2.mirrora.utils.WebUtils
import kotlinx.coroutines.delay
import org.webrtc.IceCandidate
import kotlin.math.PI
import kotlin.math.sin

class StartSharingActivityCompose : ComponentActivity() {

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var screenCaptureLauncher: ActivityResultLauncher<Intent>
    private var ws: WsSignalingClient? = null
    private var lastSentIce = 0
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var room: Room? = null

    // keep the user's audio choice
    private var sendSoundFlag: Boolean = true

    // Loader state
    private val isConnecting = mutableStateOf(false)
    private val connectingText = mutableStateOf("Connecting…")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        initializeScreenCaptureLauncher()

        setContent {
            val ctx = LocalContext.current

            val widthDp = LocalConfiguration.current.screenWidthDp
            val scale = when {
                widthDp >= 840 -> 1.35f
                widthDp >= 600 -> 1.18f
                else -> 1f
            }

            val grad = Brush.verticalGradient(listOf(Color(0xFFB0E1FC), Color(0xFFD6F3FC)))

            var code by remember { mutableStateOf("") }
            var includeAudio by remember { mutableStateOf(true) }

            val PAD_H_DP = 20f
            val TITLE_TOP_DP = 80f
            val GAP_BELOW_DESC_DP = 75f
            val AUDIO_ROW_BOTTOM_DP = 30f
            val BTN_HEIGHT_DP = 70f
            val CODE_TEXT_DP = 34f
            val TITLE_TEXT_DP = 40f
            val DESC_TEXT_DP = 20f
            val AUDIO_LABEL_TEXT_DP = 20f
            val AUDIO_LABEL_START_DP = 8f
            val INNER_SIDE_DP = 20f
            val SWITCH_W_DP = 50f
            val SWITCH_H_DP = 70f

            MaterialTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(grad)
                        .systemBarsPadding()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(dpScaled(TITLE_TOP_DP, scale)))
                        Text(
                            text = "Share screen",
                            fontSize = spFromDpText(ctx, TITLE_TEXT_DP) * scale,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C6FBB)
                        )

                        Text(
                            text = "Enter the code to start sharing",
                            fontSize = spFromDpText(ctx, DESC_TEXT_DP) * scale,
                            color = Color(0xFF3691F0),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(dpScaled(GAP_BELOW_DESC_DP, scale)))

                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = dpScaled(INNER_SIDE_DP, scale),
                                    end = dpScaled(INNER_SIDE_DP, scale)
                                ),
                            factory = { context ->
                                android.widget.EditText(context).apply {
                                    background = ContextCompat.getDrawable(
                                        context,
                                        R.drawable.edit_text_background
                                    )
                                    hint = "000000"
                                    setHintTextColor(Color(0xFF6B7280).toArgb())
                                    setTextColor(ContextCompat.getColor(context, R.color.colorPrimary))
                                    setTextSize(
                                        TypedValue.COMPLEX_UNIT_SP,
                                        spFloatFromDpText(context, CODE_TEXT_DP) * scale
                                    )
                                    setTypeface(
                                        android.graphics.Typeface.MONOSPACE,
                                        android.graphics.Typeface.BOLD
                                    )
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
                                            android.text.Selection.removeSelection(e.text)
                                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
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
                                et.addTextChangedListener(object : android.text.TextWatcher {
                                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                        val v = s?.toString().orEmpty().take(6)
                                        if (v != code) code = v
                                    }
                                    override fun afterTextChanged(s: android.text.Editable?) {}
                                })
                            }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = dpScaled(INNER_SIDE_DP, scale),
                                    end = dpScaled(INNER_SIDE_DP, scale),
                                    bottom = dpScaled(AUDIO_ROW_BOTTOM_DP, scale)
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Include device audio",
                                fontSize = spFromDpText(ctx, AUDIO_LABEL_TEXT_DP) * scale,
                                color = Color(0xFF6B7280),
                                modifier = Modifier.padding(start = dpScaled(AUDIO_LABEL_START_DP, scale))
                            )
                            Spacer(Modifier.weight(1f))
                            AndroidView(
                                modifier = Modifier
                                    .width(dpScaled(SWITCH_W_DP, scale))
                                    .height(dpScaled(SWITCH_H_DP, scale)),
                                factory = { context ->
                                    SwitchCompat(context).apply {
                                        showText = false
                                        isChecked = includeAudio
                                        minWidth = 0
                                        setSwitchMinWidth(dpToPx(context, dpScaled(SWITCH_W_DP, scale)))
                                        splitTrack = false
                                        switchPadding = 0
                                        trackTintList = ContextCompat.getColorStateList(context, R.color.toggle_track)
                                        thumbTintList = ContextCompat.getColorStateList(context, R.color.toggle_thumb)
                                        setOnCheckedChangeListener { _, checked -> includeAudio = checked }
                                    }
                                },
                                update = { sw -> if (sw.isChecked != includeAudio) sw.isChecked = includeAudio }
                            )
                        }

                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = dpScaled(PAD_H_DP, scale),
                                    end = dpScaled(PAD_H_DP, scale),
                                    bottom = dpScaled(20f, scale)
                                )
                                .height(dpScaled(BTN_HEIGHT_DP, scale)),
                            factory = { context ->
                                AppCompatButton(context).apply {
                                    background = ContextCompat.getDrawable(context, R.drawable.rounded_button)
                                    isAllCaps = false
                                    setTextColor(Color.White.toArgb())
                                    text = "Start sharing"
                                    setTextSize(
                                        TypedValue.COMPLEX_UNIT_SP,
                                        spFloatFromDpText(context, 20f) * scale
                                    )
                                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                                    setOnClickListener {
                                        if (isRoomCodeValid(code)) {
                                            sendSoundFlag = includeAudio
                                            room = Room(code)
                                            requestScreenCaptureConsent()
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
                    ws?.close()
                    ws = null
                    isConnecting.value = false
                } else {
                    // Default back behavior
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        ws?.close()
        super.onDestroy()
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

    private fun initializeScreenCaptureLauncher() {
        screenCaptureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                room?.let { connectToSignaling(result.resultCode, result.data!!, it) }
            } else {
                isConnecting.value = false
                Toast.makeText(this, "Consent denied.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestScreenCaptureConsent() {
        val intent = projectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(intent)
    }

    private fun connectToSignaling(resultCode: Int, data: Intent, room: Room) {
        isConnecting.value = true
        connectingText.value = "Connecting"

        ws = WsSignalingClient(
            WebUtils.SERVER_IP_ADDRESS,
            room.roomNumber,
            object : WsSignalingClient.Listener {
                override fun onOpen() {
                    connectingText.value = "Connected — joining room…"
                }

                override fun onJoined(roomCode: String, peers: Int) {
                    // Start media projection service when we have the room
                    ScreenShareService.start(
                        this@StartSharingActivityCompose,
                        resultCode,
                        data,
                        sendSoundFlag,
                        room
                    )
                    // Navigate now that things are in a good state
                    val i = Intent(this@StartSharingActivityCompose, SharingActivityCompose::class.java)
                    i.putExtra("room", room)
                    startActivity(i)

                    isConnecting.value = false
                    Toast.makeText(
                        this@StartSharingActivityCompose,
                        "Joined room $roomCode  (peers=$peers)",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onReady(room: String) {
                    flushOfferAndIce(room)
                }

                override fun onAnswer(r: String, sdp: String) {
                    ScreenShareService.applyRemoteAnswer(sdp)
                }

                override fun onIce(c: IceCandidate) {
                    ScreenShareService.addRemoteIce(c)
                }

                override fun onPeerLeft() {
                    Toast.makeText(this@StartSharingActivityCompose, "Peer left", Toast.LENGTH_SHORT).show()
                }

                override fun onError(reason: String) {
                    isConnecting.value = false
                    Toast.makeText(this@StartSharingActivityCompose, "Server unreachable. Please try again.", Toast.LENGTH_SHORT).show()
                }

                override fun onClosed() {
                    isConnecting.value = false
//                    Toast.makeText(this@StartSharingActivityCompose, "WS closed", Toast.LENGTH_SHORT).show()
                }

                override fun onOffer(room: String, sdp: String) {}
            },
            WsSignalingClient.Role.SENDER
        )
        ws?.connect()
    }

    private fun flushOfferAndIce(room: String) {
        val offer = ScreenShareService.getLocalOfferSdp()
        if (offer.isNullOrEmpty()) {
            main.postDelayed({ flushOfferAndIce(room) }, 100)
            return
        }
        ws?.sendOffer(room, offer)
        sendAnyNewIce(room)
        main.postDelayed({ sendAnyNewIce(room) }, 300)
        main.postDelayed({ sendAnyNewIce(room) }, 800)
        main.postDelayed({ sendAnyNewIce(room) }, 1500)
    }

    private fun sendAnyNewIce(room: String) {
        val list = ScreenShareService.getLocalIceCandidatesSnapshot()
        for (i in lastSentIce until list.size) {
            ws?.sendIce(room, list[i])
        }
        lastSentIce = list.size
    }
}
