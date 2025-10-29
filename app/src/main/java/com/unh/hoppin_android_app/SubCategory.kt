package com.unh.hoppin_android_app
data class SubCategory(
    val id: String,           // stable key, e.g. "museums"
    val title: String,        // UI label, e.g. "Museums"
    val includedTypes: List<String> // Places API type strings (lowercase)
)