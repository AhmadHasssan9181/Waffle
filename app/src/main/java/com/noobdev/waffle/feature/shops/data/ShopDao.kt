package com.noobdev.waffle.feature.shops.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ShopDao {

    @Query(
            "SELECT * FROM shops WHERE lat BETWEEN :minLat AND :maxLat " +
                    "AND lon BETWEEN :minLon AND :maxLon"
    )
    suspend fun inBox(
            minLat: Double,
            maxLat: Double,
            minLon: Double,
            maxLon: Double
    ): List<ShopEntity>

    @Query(
            "SELECT MAX(fetchedAt) FROM shops WHERE lat BETWEEN :minLat AND :maxLat " +
                    "AND lon BETWEEN :minLon AND :maxLon"
    )
    suspend fun newestInBox(
            minLat: Double,
            maxLat: Double,
            minLon: Double,
            maxLon: Double
    ): Long?

    @Upsert suspend fun upsertAll(shops: List<ShopEntity>)

    @Query("UPDATE shops SET photoUrl = :url, photoCredit = :credit WHERE id = :id")
    suspend fun setPhoto(id: String, url: String?, credit: String?)

    @Query("DELETE FROM shops WHERE fetchedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
}
