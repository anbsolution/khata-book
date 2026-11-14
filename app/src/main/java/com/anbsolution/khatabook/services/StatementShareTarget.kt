package com.anbsolution.khatabook.services

enum class StatementShareTarget(
    val storageValue: String,
    val label: String,
    val whatsappPackages: List<String>
) {
    AUTOMATIC(
        storageValue = "automatic",
        label = "Automatic",
        whatsappPackages = listOf("com.whatsapp", "com.whatsapp.w4b")
    ),
    WHATSAPP(
        storageValue = "whatsapp",
        label = "WhatsApp",
        whatsappPackages = listOf("com.whatsapp")
    ),
    WHATSAPP_BUSINESS(
        storageValue = "whatsapp_business",
        label = "WhatsApp Business",
        whatsappPackages = listOf("com.whatsapp.w4b")
    ),
    SYSTEM_SHARE(
        storageValue = "system_share",
        label = "Android share sheet",
        whatsappPackages = emptyList()
    );

    companion object {
        fun fromStorageValue(value: String?): StatementShareTarget =
            entries.firstOrNull { it.storageValue == value } ?: AUTOMATIC
    }
}
