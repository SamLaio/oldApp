package com.samliao.findpacket

data class Carrier(
    val name: String,
    val url: String,
    val autofillSelector: String? = null,
) {
    override fun toString(): String = name
}
