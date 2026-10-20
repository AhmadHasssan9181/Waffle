package com.noobdev.waffle.feature.shops.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ShopEntity::class], version = 1, exportSchema = false)
abstract class ShopDatabase : RoomDatabase() {

    abstract fun shopDao(): ShopDao

    companion object {
        @Volatile private var instance: ShopDatabase? = null

        fun get(context: Context): ShopDatabase =
                instance
                        ?: synchronized(this) {
                            instance
                                    ?: Room.databaseBuilder(
                                                    context.applicationContext,
                                                    ShopDatabase::class.java,
                                                    "waffle_shops.db"
                                            )
                                            .fallbackToDestructiveMigration()
                                            .build()
                                            .also { instance = it }
                        }
    }
}
