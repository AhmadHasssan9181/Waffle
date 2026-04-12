package com.noobdev.waffle.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.noobdev.waffle.ui.components.StatusLevel

class Converters {

    private val gson = Gson()

    @TypeConverter
    fun fromStringMap(value: String?): Map<String, Any>? {
        if (value == null) return null
        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        return try {
            gson.fromJson(value, mapType)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    @TypeConverter
    fun toStringMap(map: Map<String, Any>?): String? {
        if (map == null) return null
        return gson.toJson(map)
    }

    @TypeConverter
    fun fromStatusLevel(status: StatusLevel?): String? {
        return status?.name
    }

    @TypeConverter
    fun toStatusLevel(status: String?): StatusLevel? {
        if (status == null) return null
        return try {
            StatusLevel.valueOf(status)
        } catch (e: Exception) {
            StatusLevel.INFO // fallback
        }
    }
}

