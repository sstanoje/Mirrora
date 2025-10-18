package com.s2d2.mirrora.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.s2d2.mirrora.R
import com.s2d2.mirrora.ScreenShareService
import com.s2d2.mirrora.connection.Room

class SharingActivityCompose : ComponentActivity() {

    private var room: Room? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        room = intent.getSerializableExtra("room") as? Room

        setContent {
            val ctx = LocalContext.current

            val widthDp = LocalConfiguration.current.screenWidthDp
            val scale = when {
                widthDp >= 840 -> 1.35f
                widthDp >= 600 -> 1.18f
                else -> 1f
            }

            val bg = Brush.verticalGradient(listOf(Color(0xFFB0E1FC), Color(0xFFD6F3FC)))

            val PAD_SIDE = 0f
            val TITLE_TOP = 80f
            val TITLE_BOTTOM = 10f
            val BADGE_BOTTOM = 80f
            val LABEL_SIDE = 20f
            val CODE_SIDE = 20f
            val CODE_BOTTOM = 50f
            val BTN_SIDE = 35f
            val BTN_HEIGHT = 70f
            val BTN_BOTTOM = 20f

            val TITLE_TXT_DP = 40f
            val LABEL_TXT_DP = 26f
            val CODE_TXT_DP = 50f
            val BTN_TXT_DP = 20f

            val roomNumber = remember(room) { room?.roomNumber ?: "000000" }

            MaterialTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(bg)
                        .systemBarsPadding()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(dpScaled(TITLE_TOP, scale)))
                        Text(
                            text = "Sharing live",
                            fontSize = spFromDpText(ctx, TITLE_TXT_DP) * scale,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C6FBB),
                            modifier = Modifier.padding(bottom = dpScaled(TITLE_BOTTOM, scale))
                        )

                        AndroidView(
                            modifier = Modifier
                                .wrapContentSize()
                                .padding(bottom = dpScaled(BADGE_BOTTOM, scale)),
                            factory = { context ->
                                val v = LayoutInflater.from(context)
                                    .inflate(R.layout.view_live_badge, null, false)

                                val ring = v.findViewById<View>(R.id.pulseRing)
                                ring?.post {
                                    val anim =
                                        AnimationUtils.loadAnimation(context, R.anim.pulse_out)
                                    anim.repeatCount = Animation.INFINITE
                                    ring.startAnimation(anim)
                                }
                                v
                            },
                            update = { v ->
                                val ring = v.findViewById<View>(R.id.pulseRing)
                                if (ring?.animation == null) {
                                    val anim =
                                        AnimationUtils.loadAnimation(v.context, R.anim.pulse_out)
                                    anim.repeatCount = Animation.INFINITE
                                    ring?.startAnimation(anim)
                                }
                            }
                        )

                        Text(
                            text = "Room code:",
                            fontSize = spFromDpText(ctx, LABEL_TXT_DP) * scale,
                            color = Color(0xFF2C6FBB),
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dpScaled(LABEL_SIDE, scale))
                                .wrapContentHeight()
                                .then(Modifier),
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = roomNumber,
                            fontSize = spFromDpText(ctx, CODE_TXT_DP) * scale,
                            color = Color(0xFF2C6FBB),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = dpScaled(CODE_SIDE, scale),
                                    end = dpScaled(CODE_SIDE, scale),
                                    bottom = dpScaled(CODE_BOTTOM, scale)
                                ),
                            textAlign = TextAlign.Center
                        )

                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = dpScaled(BTN_SIDE, scale),
                                    end = dpScaled(BTN_SIDE, scale),
                                    bottom = dpScaled(BTN_BOTTOM, scale)
                                )
                                .height(dpScaled(BTN_HEIGHT, scale)),
                            factory = { context ->
                                AppCompatButton(context).apply {
                                    background = ContextCompat.getDrawable(
                                        context,
                                        R.drawable.rounded_button_red
                                    )
                                    isAllCaps = false
                                    setTextColor(Color.White.toArgb())
                                    text = "Stop"

                                    setTextSize(
                                        TypedValue.COMPLEX_UNIT_SP,
                                        spFloatFromDpText(
                                            context,
                                            BTN_TXT_DP
                                        ) * scale
                                    )

                                    setTypeface(typeface, Typeface.BOLD)

                                    gravity = Gravity.CENTER

                                    setOnClickListener {
                                        ScreenShareService.stop(this@SharingActivityCompose)
                                        startActivity(
                                            Intent(
                                                this@SharingActivityCompose,
                                                StartActivityCompose::class.java
                                            )
                                        )
                                        finish()
                                    }
                                }
                            }
                        )
                    }

                    LaunchedEffect(Unit) {
                        val root = window.decorView.findViewById<View>(android.R.id.content)
                        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
                            insets
                        }
                    }
                }
            }

            BackHandler {
                Toast.makeText(this, "You have to stop sharing to leave.", Toast.LENGTH_SHORT).show()
                return@BackHandler
            }
        }
    }
}