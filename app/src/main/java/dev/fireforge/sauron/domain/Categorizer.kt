package dev.fireforge.sauron.domain

data class CategorizationRule(val category: String, val keywords: List<String>)

object Categorizer {

    val rules: List<CategorizationRule> = listOf(
        CategorizationRule(
            category = "Food & Drink",
            keywords = listOf(
                "แมคโดนัลด์", "แมคไทย", "mcdonald", "kfc", "starbucks", "สตาร์บัคส์",
                "กาแฟ", "coffee", "cafe", "ร้านอาหาร", "อาหาร", "ฟู้ด",
                "7-eleven", "เซเว่น", "พิซซ่า", "pizza", "ชาบู", "sushi",
                "สุกี้", "หมูกระทะ", "ชา", "นม", "เบเกอรี่", "bakery",
                "ข้าว", "ตำ",
            ),
        ),
        CategorizationRule(
            category = "Transport",
            keywords = listOf(
                "แท็กซี่", "taxi", "grab", "bts", "mrt", "รถไฟฟ้า",
                "ใต้ดิน", "ขนส่ง", "ไปรษณีย์", "ปตท.", "ปั้ม", "น้ำมัน",
                "เบนซิน", "shell", "bangchak", "บางจาก", "ทางด่วน", "expressway",
                "จอดรถ", "parking",
            ),
        ),
        CategorizationRule(
            category = "Shopping",
            keywords = listOf(
                "big c", "bigc", "lotus", "เทสโก้", "เซ็นทรัล", "central",
                "robinson", "โรบินสัน", "shopee", "ช้อปปี้", "lazada", "ลาซาด้า",
                "zara", "uniqlo", "ห้าง", "ดีพาร์ทเมนท์", "department",
            ),
        ),
        CategorizationRule(
            category = "Bills",
            keywords = listOf(
                "ค่าไฟ", "ค่าน้ำ", "ค่าเน็ต", "ค่าโทร", "ais", "trueonline",
                "3bb", "nt", "โทรศัพท์", "internet", "ประปา", "การไฟฟ้า",
                "insurance", "ประกัน",
            ),
        ),
        CategorizationRule(
            category = "Income",
            keywords = listOf(
                "เงินเดือน", "salary", "โบนัส", "bonus", "ดอกเบี้ย", "interest",
                "เงินเข้า", "ค่าคอม", "commission",
            ),
        ),
    )

    fun categorize(merchant: String): String? =
        rules.firstOrNull { rule -> rule.keywords.any { matches(merchant, it) } }?.category

    // Thai has no letter case, so case-insensitive matching applies to ASCII keywords only.
    private fun matches(merchant: String, keyword: String): Boolean =
        if (keyword.all { it.code < 128 }) {
            merchant.contains(keyword, ignoreCase = true)
        } else {
            merchant.contains(keyword)
        }
}
