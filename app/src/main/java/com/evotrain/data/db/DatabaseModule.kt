package com.evotrain.data.db

import android.content.Context
import androidx.room.Room
import com.evotrain.data.model.AIModel
import com.evotrain.data.model.Generation
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "evotrain_database"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideModelDao(database: AppDatabase): ModelDao {
        return database.modelDao()
    }

    @Provides
    fun provideGenerationDao(database: AppDatabase): GenerationDao {
        return database.generationDao()
    }

    @Provides
    fun provideInferenceResultDao(database: AppDatabase): InferenceResultDao {
        return database.inferenceResultDao()
    }
}
