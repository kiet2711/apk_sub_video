package com.capcut.capsub.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capcut.capsub.data.repository.SettingsRepository
import com.capcut.capsub.ui.theme.CardBorder
import com.capcut.capsub.ui.theme.DarkBackground
import com.capcut.capsub.ui.theme.DarkSurface
import com.capcut.capsub.ui.theme.PrimaryEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }

    var keysText by remember { mutableStateOf(repo.geminiApiKeys.joinToString("\n")) }
    var threadCount by remember { mutableStateOf(repo.geminiThreadCount.toFloat()) }
    var fontSize by remember { mutableStateOf(repo.subtitleFontSizeSp) }
    var blackBoxOpacity by remember { mutableStateOf(repo.blackBoxOpacity) }
    var downloadThreadCount by remember { mutableFloatStateOf(repo.downloadThreadCount.toFloat()) }
    var bilibiliSessData by remember { mutableStateOf(repo.bilibiliSessData) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cài Đặt Ứng Dụng", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. QUẢN LÝ GEMINI API KEY
            Text(
                text = "GOOGLE GEMINI API KEYS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryEmerald,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Hỗ trợ nhập nhiều API Key (mỗi dòng 1 key). Ứng dụng sẽ tự động chia tải xoay vòng (Round-Robin) và tự đổi key khác ngay khi gặp lỗi 429.",
                fontSize = 13.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = keysText,
                onValueChange = { keysText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text("AIzaSy...\nAIzaSy...", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedBorderColor = PrimaryEmerald,
                    unfocusedBorderColor = Color(0xFF333544),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 2. SỐ LUỒNG DỊCH GEMINI (MULTI-THREADING)
            Text(
                text = "SỐ LUỒNG DỊCH GEMINI ĐỒNG THỜI: ${threadCount.toInt()} LUỒNG",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Chia nhỏ phụ đề và dịch song song qua nhiều API Key cùng lúc để tăng tốc độ gấp 2-5 lần.",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Slider(
                value = threadCount,
                onValueChange = { threadCount = it },
                valueRange = 1f..5f,
                steps = 3
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 3. KÍCH THƯỚC CHỮ PHỤ ĐỀ
            Text(
                text = "KÍCH THƯỚC PHỤ ĐỀ MẶC ĐỊNH: ${fontSize.toInt()} SP",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text("Có thể chỉnh nhỏ xuống 8sp cho phụ đề ngắn gọn, không chiếm chỗ", color = Color.Gray, fontSize = 12.sp)
            Slider(
                value = fontSize,
                onValueChange = { fontSize = it },
                valueRange = 8f..36f,
                steps = 27
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 4. ĐỘ ĐẬM HỘP ĐEN (BLACKBOX)
            Text(
                text = "ĐỘ MỜ HỘP ĐEN CHE SUB CỨNG: ${(blackBoxOpacity * 100).toInt()}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Slider(
                value = blackBoxOpacity,
                onValueChange = { blackBoxOpacity = it },
                valueRange = 0.3f..1.0f
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 5. CẤU HÌNH TẢI VIDEO ONLINE & BILIBILI VIP
            Text(
                text = "TẢI VIDEO ONLINE & BILIBILI VIP",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryEmerald,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "SỐ LUỒNG TẢI SONG SONG: ${downloadThreadCount.toInt()} LUỒNG",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Tải đa luồng song song luân phiên qua các cụm máy chủ CDN (Tencent, Alibaba, Huawei, Bilibili) giúp tăng tốc độ tải lên gấp 5-10 lần (Khuyên dùng: 12 - 24 luồng).",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Slider(
                value = downloadThreadCount,
                onValueChange = { downloadThreadCount = it },
                valueRange = 8f..32f,
                steps = 23
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "BILIBILI COOKIE (SESSDATA):",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Nhập mã SESSDATA tài khoản Bilibili để mở khóa xem và tải chất lượng cao 1080P+, 4K và các video giới hạn VIP.",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = bilibiliSessData,
                onValueChange = { bilibiliSessData = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Dán SESSDATA hoặc toàn bộ Cookie...", color = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedBorderColor = PrimaryEmerald,
                    unfocusedBorderColor = Color(0xFF333544),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 6. THÔNG TIN ỨNG DỤNG & CẬP NHẬT GITHUB
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(12.dp))
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kiet2711/apk_sub_video"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Không thể mở trình duyệt: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Update,
                                contentDescription = null,
                                tint = PrimaryEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "KHO LƯU TRỮ GỐC (GITHUB)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "https://github.com/kiet2711/apk_sub_video",
                        fontSize = 12.sp,
                        color = Color(0xFF64B5F6),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Đây là repository chính thức của ứng dụng. Bấm vào để theo dõi mã nguồn và tải các bản cập nhật mới nhất.",
                        fontSize = 12.sp,
                        color = Color(0xFFC0C0C0)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 7. LƯU CÀI ĐẶT
            Button(
                onClick = {
                    val keyList = keysText.split(",", ";", "\n").map { it.trim() }.filter { it.isNotBlank() }
                    repo.geminiApiKeys = keyList
                    repo.geminiThreadCount = threadCount.toInt()
                    repo.subtitleFontSizeSp = fontSize
                    repo.blackBoxOpacity = blackBoxOpacity
                    repo.downloadThreadCount = downloadThreadCount.toInt()
                    repo.bilibiliSessData = bilibiliSessData
                    Toast.makeText(context, "Đã lưu cài đặt thành công!", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryEmerald,
                    contentColor = Color.Black
                )
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Text(" Lưu Cài Đặt", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
