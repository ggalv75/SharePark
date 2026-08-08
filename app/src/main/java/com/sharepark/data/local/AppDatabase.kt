package com.sharepark.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sharepark.data.local.dao.AutomationRuleDao
import com.sharepark.data.local.dao.ParkingRecordDao
import com.sharepark.data.local.dao.TrustedContactDao
import com.sharepark.data.local.dao.VehicleDao
import com.sharepark.data.local.entity.AutomationRuleEntity
import com.sharepark.data.local.entity.ParkingRecordEntity
import com.sharepark.data.local.entity.TrustedContactEntity
import com.sharepark.data.local.entity.VehicleEntity

@Database(
    entities = [
        VehicleEntity::class,
        ParkingRecordEntity::class,
        TrustedContactEntity::class,
        AutomationRuleEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun parkingRecordDao(): ParkingRecordDao
    abstract fun trustedContactDao(): TrustedContactDao
    abstract fun automationRuleDao(): AutomationRuleDao

    companion object {
        const val DATABASE_NAME = "sharepark_db"
    }
}
