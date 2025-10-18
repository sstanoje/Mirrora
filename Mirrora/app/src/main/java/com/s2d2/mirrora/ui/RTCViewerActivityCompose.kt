package com.s2d2.mirrora.ui

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import com.s2d2.mirrora.R
import com.s2d2.mirrora.utils.WebUtils
import com.s2d2.mirrora.connection.WsSignalingClient
import com.s2d2.mirrora.connection.ConnectionConfig
import com.s2d2.mirrora.connection.ConnectionController
import com.s2d2.mirrora.connection.ConnectionRole
import com.s2d2.mirrora.connection.DefaultConnectionFactory
import com.s2d2.mirrora.connection.Room
import com.s2d2.mirrora.connection.ViewerController
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

class RTCViewerActivityCompose : ComponentActivity() {

    companion object { private const val EXTRA_ROOM = "ROOM" }

    private var remoteRenderer: SurfaceViewRenderer? = null

    private var ws: WsSignalingClient? = null
    private var lastSentIce = 0
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private var viewer: ViewerController? = null
    private var room: Room? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        room = intent.getSerializableExtra(EXTRA_ROOM) as? Room

        onBackPressedDispatcher.addCallback(this) {
            startActivity(Intent(this@RTCViewerActivityCompose, StartActivityCompose::class.java))
            finishAffinity()
        }

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        routeToSpeakerAndMaxVolume()
        forceWebRtcPlayoutAsMedia()

        val cfg = ConnectionConfig.Builder()
            .roomCode(room?.roomNumber ?: "")
            .signalingUrl(WebUtils.SERVER_IP_ADDRESS)
            .iceServers(
                listOf(
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
                )
            )
            .build()

        val created: ConnectionController =
            DefaultConnectionFactory.getInstance().create(ConnectionRole.VIEWER, cfg, this)
        viewer = (created as ViewerController)
        viewer?.start()

        setContent {
            val ctx = LocalContext.current

            val widthDp = LocalConfiguration.current.screenWidthDp
            val scale = when {
                widthDp >= 840 -> 1.35f
                widthDp >= 600 -> 1.18f
                else -> 1f
            }

            val TITLE_TXT_DP = 40f
            val LABEL_TXT_DP = 26f
            val CODE_TXT_DP  = 50f
            val TITLE_BOTTOM = 10f
            val CODE_BOTTOM  = 50f
            val SIDE_20      = 20f
            val LOGO_SIZE_DP = 120f

            var isVideoVisible = false
            val roomNumber = remember(room) { room?.roomNumber ?: "000000" }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .systemBarsPadding()
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        SurfaceViewRenderer(context).also { r ->
                            remoteRenderer = r

                            val eglCtx: EglBase.Context? = viewer?.eglBaseContext
                            r.init(eglCtx, null)
                            r.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                            r.setEnableHardwareScaler(true)
                            r.setMirror(false)
                            r.visibility = View.INVISIBLE

                            viewer?.attachRemoteSink(r)
                        }
                    },
                    update = { r ->
                        r.visibility = if (isVideoVisible) View.VISIBLE else View.INVISIBLE
                        if (isVideoVisible) {
                            val lp: ViewGroup.LayoutParams = r.layoutParams
                            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                            r.layoutParams = lp
                            r.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                            r.requestLayout()
                        }
                    }
                )

                if (!isVideoVisible) {
                    val grad = Brush.verticalGradient(listOf(Color(0xFFB0E1FC), Color(0xFFD6F3FC)))
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(grad)
                            .padding(
                                start = dpScaled(SIDE_20, scale),
                                end = dpScaled(SIDE_20, scale)
                            )
                            .wrapContentSize(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.mirrora_transparent),
                            contentDescription = null,
                            modifier = Modifier.size(dpScaled(LOGO_SIZE_DP, scale))
                        )
                        Spacer(Modifier.height(dpScaled(16f, scale)))

                        Text(
                            text = "Waiting for host",
                            fontSize = spFromDpText(ctx, TITLE_TXT_DP) * scale,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C6FBB),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(
                                bottom = dpScaled(
                                    TITLE_BOTTOM,
                                    scale
                                )
                            )
                        )

                        Text(
                            text = "Room code:",
                            fontSize = spFromDpText(ctx, LABEL_TXT_DP) * scale,
                            color = Color(0xFF2C6FBB),
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dpScaled(SIDE_20, scale))
                        )

                        Text(
                            text = roomNumber,
                            fontSize = spFromDpText(ctx, CODE_TXT_DP) * scale,
                            color = Color(0xFF2C6FBB),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = dpScaled(SIDE_20, scale),
                                    end = dpScaled(SIDE_20, scale),
                                    bottom = dpScaled(CODE_BOTTOM, scale)
                                )
                        )
                    }
                }

                LaunchedEffect(Unit) {
                    val root = window.decorView.findViewById<View>(android.R.id.content)
                    ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets -> insets }
                }

                LaunchedEffect(Unit) {
                    setupWs(
                        onShowVideo = {
                            isVideoVisible = true
                            showVideoFullScreen()
                        },
                        onShowLobby = {
                            isVideoVisible = false
                            showLobby()
                        }
                    )
                }
            }
        }
    }

    private fun setupWs(onShowVideo: () -> Unit, onShowLobby: () -> Unit) {
        val r = room ?: return
        ws = WsSignalingClient(
            WebUtils.SERVER_IP_ADDRESS,
            r.roomNumber,
            object : WsSignalingClient.Listener {
                override fun onOpen() {}
                override fun onJoined(room: String, peers: Int) {}
                override fun onReady(room: String) {}

                override fun onOffer(room: String, sdp: String) {
                    viewer?.applyRemoteOffer(sdp)
                    flushAnswerAndIce(r.roomNumber)
                }

                override fun onAnswer(room: String, sdp: String) {}

                override fun onIce(c: IceCandidate) {
                    viewer?.addRemoteIce(c)
                }

                override fun onPeerLeft() {
                    toast("Peer left")
                    onShowLobby()
                }

                override fun onError(reason: String) {
                    toast("WS error: $reason")
                }

                override fun onClosed() {}
            },
            WsSignalingClient.Role.VIEWER
        )
        ws?.connect()
    }

    private fun flushAnswerAndIce(room: String) {
        val answer = viewer?.localAnswerSdp
        if (answer.isNullOrEmpty()) {
            main.postDelayed({ flushAnswerAndIce(room) }, 100)
            return
        }
        ws?.sendAnswer(room, answer)

        sendAnyNewIce(room)

        main.postDelayed({ sendAnyNewIce(room) }, 300)
        main.postDelayed({ sendAnyNewIce(room) }, 800)
        main.postDelayed({ sendAnyNewIce(room) }, 1500)

        showVideoFullScreen()
    }

    private fun sendAnyNewIce(room: String) {
        val list = viewer?.localIceCandidatesSnapshot ?: emptyList()
        for (i in lastSentIce until list.size) {
            ws?.sendIce(room, list[i])
        }
        lastSentIce = list.size
    }

    private fun showLobby() {
        runOnUiThread {
            remoteRenderer?.clearImage()
            remoteRenderer?.visibility = View.INVISIBLE
        }
    }

    private fun showVideoFullScreen() {
        runOnUiThread {
            remoteRenderer?.let { r ->
                val lp = r.layoutParams
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                r.layoutParams = lp
                r.visibility = View.VISIBLE
                r.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                r.requestLayout()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { ws?.close() } catch (_: Throwable) {}
        try { viewer?.stop(); viewer = null } catch (_: Throwable) {}
        try { remoteRenderer?.release(); remoteRenderer = null } catch (_: Throwable) {}
        releaseAudioFocus()
    }

    private fun routeToSpeakerAndMaxVolume() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true

        if (Build.VERSION.SDK_INT >= 26) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .build()
            audioManager.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
        val maxVoice = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVoice, 0)
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest!!)
        }
    }

    private fun forceWebRtcPlayoutAsMedia() {
        try {
            val clazz = try {
                Class.forName("org.webrtc.audio.WebRtcAudioTrack")
            } catch (_: ClassNotFoundException) {
                Class.forName("org.webrtc.voiceengine.WebRtcAudioTrack")
            }
            val m = clazz.getDeclaredMethod("setAudioTrackUsageAttribute", Int::class.javaPrimitiveType)
            m.isAccessible = true
            m.invoke(null, AudioAttributes.USAGE_MEDIA)
        } catch (_: Throwable) { /* best effort */ }
    }

    private fun toast(msg: String) =
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
}