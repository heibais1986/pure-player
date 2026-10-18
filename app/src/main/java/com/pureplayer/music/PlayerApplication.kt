package com.pureplayer.music

import android.app.Application
import androidx.room.Room
import com.pureplayer.music.data.db.AppDatabase

class PlayerApplication : Application() {
    val db: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "pureplayer.db")
            .fallbackToDestructiveMigration()
            .build()
    }
}
