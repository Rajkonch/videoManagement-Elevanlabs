package com.schotech.videoapp.data

data class Contact(
    val name: String,
    val number: String,
    val type: String = "",
    val date: String = "",
    val dateMillis: Long = 0L,
    val duration: Long = 0L,
    val isFromCallLog: Boolean = false,
    var callCount: Int = 1,
    val filePath: String? = null
)
