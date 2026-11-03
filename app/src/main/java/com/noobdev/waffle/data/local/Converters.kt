package com.noobdev.waffle.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.noobdev.waffle.data.model.StatusLevel

/**
 * Named (not anonymous) so R8 can keep the generic signature reliably. Anonymous TypeToken
 * subclasses inside a converter method are a classic source of "TypeToken must be created with a
 * type argument" crashes in release builds.
 */
private class MapTypeToken : TypeToken<Map<String, Any>>()

private val mapType = MapTypeToken().type

class Converters {

    private val gson = Gson()

    @TypeConverter
    fun fromStringMap(value: String?): Map<String, Any>? {
        if (value == null) return null
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

