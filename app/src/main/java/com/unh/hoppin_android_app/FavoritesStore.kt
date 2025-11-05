package com.unh.hoppin_android_app


import com.unh.hoppin_android_app.UiPlace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Simple in-memory Favourites store.
 * Unique by place.id. Easy to swap with Firebase later.
 */
object FavoritesStore {
    private val _items = MutableStateFlow<Map<String, UiPlace>>(emptyMap())
    val items: StateFlow<Map<String, UiPlace>> = _items

    fun add(place: UiPlace) {
        _items.update { it + (place.id to place) }
    }

    fun remove(placeId: String) {
        _items.update { it - placeId }
    }

    fun contains(placeId: String): Boolean = _items.value.containsKey(placeId)
}
