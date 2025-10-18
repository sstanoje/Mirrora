package com.s2d2.mirrora.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.s2d2.mirrora.R

class StartActivityCompose : ComponentActivity() {

    private val REQ_ALL_PERMS = 4001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Identical background kao @drawable/gradient_background (#B0E1FC → #D6F3FC)
            val gradient = Brush.verticalGradient(
                listOf(Color(0xFFB0E1FC), Color(0xFFD6F3FC))
            )

            MaterialTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(gradient)
                        .systemBarsPadding()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // ImageView: width/height=100dp, marginTop=100dp
                        Spacer(Modifier.height(100.dp))
                        Image(
                            painter = painterResource(R.drawable.mirrora_transparent),
                            contentDescription = "Mirrora logo",
                            modifier = Modifier.size(100.dp)
                        )

                        // TextView: textSize=40dp (koristimo 40.sp da vizuelno bude isto), bold, color #2C6FBB
                        Text(
                            text = "Mirrora",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C6FBB),
                            modifier = Modifier
                                .padding(bottom = 125.dp) // layout_marginBottom
                        )

                        // Prvo dugme: match_parent, height=70dp, marginStart/End=20dp, marginBottom=20dp,
                        // background=@drawable/rounded_button (colorPrimary), textColor=#FFFFFF, bold, textSize=20dp
                        PrimarySolidButton(
                            text = "Share screen",
                            height = 70.dp,
                            onClick = { requestAllRuntimePermissionsThenStart() },
                            // marginStart/End=20dp iz XML-a, pored root paddinga
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                        )

                        // Drugo dugme: match_parent, height=70dp, marginStart/End=20dp, marginBottom=20dp,
                        // background=@drawable/rounded_button_light (colorSecondary),
                        // textColor=#3691F0, bold, textSize=20dp
                        SecondarySolidButton(
                            text = "Join screen",
                            height = 70.dp,
                            onClick = {
                                startActivity(
                                    Intent(
                                        this@StartActivityCompose,
                                        JoinScreenActivityCompose::class.java
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                        )
                    }
                }
            }
        }
    }

    // --- Permissions: isto ponašanje kao u tvom Java kodu ---
    private fun requestAllRuntimePermissionsThenStart() {
        val needed = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_ALL_PERMS)
        } else {
            startSharingActivity()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_ALL_PERMS) return

        var micGranted = true
        var notifGranted = true

        permissions.forEachIndexed { i, p ->
            val granted = grantResults.getOrNull(i) == PackageManager.PERMISSION_GRANTED
            if (p == Manifest.permission.RECORD_AUDIO) micGranted = granted
            if (p == Manifest.permission.POST_NOTIFICATIONS) notifGranted = granted
        }

        if (!micGranted) {
            Toast.makeText(this, "Mic permission is required for audio.", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && !notifGranted) {
            Toast.makeText(
                this,
                "Notifications not granted — foreground notification may be limited.",
                Toast.LENGTH_LONG
            ).show()
        }

        startSharingActivity()
    }

    private fun startSharingActivity() {
        startActivity(Intent(this, StartSharingActivityCompose::class.java))
    }
}

@Composable
private fun PrimarySolidButton(
    text: String,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF337FDD)),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp
        )
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
    }
}

@Composable
private fun SecondarySolidButton(
    text: String,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA8E0FE)),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp
        )
    ) {
        Text(
            text = text,
            color = Color(0xFF3691F0),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
    }
}
