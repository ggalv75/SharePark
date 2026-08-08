package com.sharepark.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sharepark.data.local.entity.TrustedContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrustedContactDao {

    @Query("SELECT * FROM trusted_contacts ORDER BY created_at ASC")
    fun getAllContacts(): Flow<List<TrustedContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: TrustedContactEntity): Long

    @Delete
    suspend fun delete(contact: TrustedContactEntity)
}
