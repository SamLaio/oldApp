package com.samliao.findpacket

data class Carrier(
    val name: String,
    val url: String,
    val autofillSelector: String? = null,
    val unavailable: Boolean = false,
) {
    override fun toString(): String = if (unavailable) "$name（失聯中）" else name
}
