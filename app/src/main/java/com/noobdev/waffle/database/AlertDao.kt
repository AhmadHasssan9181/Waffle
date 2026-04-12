package com.noobdev.waffle.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM disaster_alerts ORDER BY timestamp DESC")
    fun getAllAlertsFlow(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM disaster_alerts")
    suspend fun getAllAlerts(): List<AlertEntity>

    @Query("SELECT * FROM disaster_alerts WHERE id = :id LIMIT 1")
    suspend fun getAlertById(id: String): AlertEntity?

    @Query("SELECT * FROM disaster_alerts WHERE type = :type ORDER BY timestamp DESC")
    fun getAlertsByTypeFlow(type: String): Flow<List<AlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(alerts: List<AlertEntity>)

    @Query("DELETE FROM disaster_alerts")
    suspend fun clearAlerts()

    @Query("DELETE FROM disaster_alerts WHERE id = :id")
    suspend fun deleteAlertById(id: String)
}

