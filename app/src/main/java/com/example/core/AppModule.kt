package com.example.core

import android.content.Context
import androidx.room.Room
import com.example.R
import com.example.data.local.ActionLogDao
import com.example.data.local.AxtraDatabase
import com.example.data.local.BlockedAppDao
import com.example.data.local.ExpenseDao
import com.example.data.local.MemoryDao
import com.example.data.local.NoteDao
import com.example.data.local.RoutineDao
import com.example.data.local.TodoDao
import com.example.utils.WeatherApiService
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideWeatherApiService(client: OkHttpClient, moshi: Moshi): WeatherApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WeatherApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(@ApplicationContext context: Context): FirebaseFirestore {
        return FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideAxtraDatabase(@ApplicationContext context: Context): AxtraDatabase {
        return Room.databaseBuilder(
            context,
            AxtraDatabase::class.java,
            "axtra_ai_db"
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }

    @Provides fun provideMemoryDao(db: AxtraDatabase): MemoryDao = db.memoryDao()
    @Provides fun provideRoutineDao(db: AxtraDatabase): RoutineDao = db.routineDao()
    @Provides fun provideActionLogDao(db: AxtraDatabase): ActionLogDao = db.actionLogDao()
    @Provides fun provideNoteDao(db: AxtraDatabase): NoteDao = db.noteDao()
    @Provides fun provideExpenseDao(db: AxtraDatabase): ExpenseDao = db.expenseDao()
    @Provides fun provideTodoDao(db: AxtraDatabase): TodoDao = db.todoDao()
    @Provides fun provideBlockedAppDao(db: AxtraDatabase): BlockedAppDao = db.blockedAppDao()
}
