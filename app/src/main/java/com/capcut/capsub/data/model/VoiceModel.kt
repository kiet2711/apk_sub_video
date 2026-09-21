package com.capcut.capsub.data.model

import kotlinx.serialization.Serializable

@Serializable
data class VoiceItem(
    val voiceType: String,
    val displayName: String,
    val resourceId: String,
    val lang: String = "vi-VN",
    val description: String = ""
)

object VoicePresets {
    val VIETNAMESE_VOICES: List<VoiceItem> = listOf(
        VoiceItem("ICL_uranus_vi_female_yuenan1", "Chị Dịu Dàng", "7675984617253375253", description = "Dịu dàng, truyền cảm"),
        VoiceItem("ICL_uranus_vi_female_yuenan4", "Nàng Điềm Tĩnh", "7675974812837219592", description = "Điềm đạm, rõ ràng"),
        VoiceItem("ICL_uranus_vi_female_yuenan3", "Em Gái Ngọt Ngào", "7675982579366956308", description = "Ngọt ngào, đáng yêu"),
        VoiceItem("multi_female_partner_uranus_bigtts", "Quản Lý Vững Vàng", "7654501692376993040", description = "Chuyên nghiệp, quyền lực"),
        VoiceItem("ICL_uranus_vi_male_xinluyin", "Người Kể Điềm Tĩnh", "7668606732947574036", description = "Trầm ấm, sâu lắng"),
        VoiceItem("ICL_uranus_vi_female_qcns", "Nàng Tươi Tắn", "7673047160434330900", description = "Vui tươi, trẻ trung"),
        VoiceItem("ICL_uranus_vi_female_housangnvsheng", "Thiếu Nữ Dịu Dàng", "7659674297782258965", description = "Nhẹ nhàng, tình cảm"),
        VoiceItem("ICL_uranus_vi_female_xfns", "Cô Gái Năng Động", "7673039258290081045", description = "Năng động, cuốn hút"),
        VoiceItem("ICL_uranus_vi_male_cfzc", "Chàng Trai Ấm Áp", "7673075012848405761", description = "Ấm áp, thân thiện"),
        VoiceItem("multi_male_felipe_uranus_bigtts", "Giọng Nam Trầm", "7637456729696996628", description = "Nam tính, điện ảnh"),
        VoiceItem("multi_female_richgirl_uranus_bigtts", "Review Phim New", "7637460351541447956", description = "Chuyên tóm tắt review phim"),
        VoiceItem("multi_female_stokie_uranus_bigtts", "Review Phim 4", "7637456729696996628", description = "Kịch tính, nhanh gọn"),
        VoiceItem("multi_female_daqi_uranus_bigtts", "Review Phim 3", "7637451983389019409", description = "Cuốn hút, hồi hộp"),
        VoiceItem("multi_female_xyf04auto_uranus_bigtts", "Review Phim 2", "7637458743197732117", description = "Truyền cảm, nhấn nhá"),
        VoiceItem("multi_female_quanweinv_uranus_bigtts", "Bản Tin 1", "7637458743197732117", description = "Thời sự, dứt khoát"),
        VoiceItem("multi_female_sisi_uranus_bigtts", "Bản Tin Nữ", "7637455857285860629", description = "Phát thanh viên nữ"),
        VoiceItem("multi_female_xinwenjieshuo_uranus_bigtts", "Nam Bản Tin", "7637455039719640327", description = "Phát thanh viên nam"),
        VoiceItem("multi_female_yangguangnv_uranus_bigtts", "Ban Mai", "7637456432522218773", description = "Trong trẻo, tươi sáng"),
        VoiceItem("multi_female_peiqi_uranus_bigtts", "Giọng Gái Mới Lớn", "7637458789033151751", description = "Hồn nhiên, tinh nghịch"),
        VoiceItem("BV075_streaming", "Thanh Niên Tự Tin", "7102355803792740865", description = "Trẻ trung, hiện đại"),
        VoiceItem("BV074_streaming", "Cô Gái Hoạt Ngôn", "7102355709945188865", description = "Nhanh nhẹn, lôi cuốn"),
        VoiceItem("vi_female_huong", "Giọng Nữ Phổ Thông", "7264854897953083905", description = "Giọng chuẩn miền Bắc"),
        VoiceItem("BV421_vivn_streaming", "Nhỏ Ngọt Ngào", "7252594014782755330", description = "Dễ thương"),
        VoiceItem("BV074_streaming_dsp", "Giọng Bé", "7550087831092251920", description = "Trẻ em ngộ nghĩnh"),
        VoiceItem("BV562_streaming", "Mai", "7483736254694035984", description = "Nhẹ nhàng"),
        VoiceItem("BV560_streaming", "Alex Đại Đế", "7483736167565758992", description = "Uy nghiêm, mạnh mẽ")
    )

    val DEFAULT_VOICE = VIETNAMESE_VOICES[0]
}
