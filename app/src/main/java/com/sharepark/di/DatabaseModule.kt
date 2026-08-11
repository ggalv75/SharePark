package com.sharepark.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sharepark.data.local.AppDatabase
import com.sharepark.data.local.dao.AutomationRuleDao
import com.sharepark.data.local.dao.AutomationZoneDao
import com.sharepark.data.local.dao.ParkingRecordDao
import com.sharepark.data.local.dao.TrustedContactDao
import com.sharepark.data.local.dao.VehicleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `trusted_contacts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `phone_number` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `automation_rules` (
                `vehicle_id` INTEGER PRIMARY KEY NOT NULL,
                `mode` TEXT NOT NULL,
                `phone` TEXT NOT NULL,
                `group_name` TEXT NOT NULL,
                `target_label` TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `automation_zones` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `label` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `latitude` REAL NOT NULL,
                `longitude` REAL NOT NULL,
                `radius_meters` INTEGER NOT NULL,
                `is_enabled` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideVehicleDao(database: AppDatabase): VehicleDao {
        return database.vehicleDao()
    }

    @Provides
    fun provideParkingRecordDao(database: AppDatabase): ParkingRecordDao {
        return database.parkingRecordDao()
    }

    @Provides
    fun provideTrustedContactDao(database: AppDatabase): TrustedContactDao {
        return database.trustedContactDao()
    }

    @Provides
    fun provideAutomationRuleDao(database: AppDatabase): AutomationRuleDao {
        return database.automationRuleDao()
    }

    @Provides
    fun provideAutomationZoneDao(database: AppDatabase): AutomationZoneDao {
        return database.automationZoneDao()
    }
}
