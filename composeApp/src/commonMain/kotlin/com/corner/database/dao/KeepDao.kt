package com.corner.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.corner.database.entity.Keep
import kotlinx.coroutines.flow.Flow

@Dao
interface KeepDao{
    @Query("SELECT * FROM Keep")
    fun getAll(): Flow<List<Keep>>
}