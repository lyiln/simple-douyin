package com.example.douyin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.douyin.network.ApiClient
import com.example.douyin.ui.DouyinApp
import com.example.douyin.ui.theme.DouyinTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 初始化 API 客户端（模拟器默认地址 http://10.0.2.2:8080）
        ApiClient.init(applicationContext)

        setContent {
            DouyinTheme {
                var showApp by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(650)
                    showApp = true
                }
                if (showApp) {
                    DouyinApp()
                } else {
                    StartupScreen()
                }
            }
        }
    }
}

@Composable
private fun StartupScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF111114)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "抖",
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
