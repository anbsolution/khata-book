package com.anbsolution.khatabook.services

internal fun normalizeIndianWhatsAppPhoneNumber(phone: String): String? {
    val digits = phone.filter(Char::isDigit)
    val nationalNumber = when {
        digits.length == 10 -> digits
        digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
        else -> return null
    }

    if (nationalNumber.first() !in '6'..'9') return null
    return "91$nationalNumber"
}
