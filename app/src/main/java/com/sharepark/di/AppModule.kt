package com.sharepark.di

import android.content.Context
import android.util.Log
import com.sharepark.BuildConfig
import com.sharepark.data.remote.GeocodingApi
import com.sharepark.data.remote.GeocodingService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val API_KEY_PARAM = Regex("""key=[^&\s]+""")

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // The Geocoding API takes the Maps key as a `key=` query parameter, so every logged URL
        // would carry it into logcat. Mask it, and log nothing at all in release builds.
        val logging = HttpLoggingInterceptor { message ->
            Log.d("OkHttp", message.replace(API_KEY_PARAM, "key=REDACTED"))
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideGeocodingApi(okHttpClient: OkHttpClient): GeocodingApi {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.GEOCODING_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeocodingApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGeocodingService(
        @ApplicationContext context: Context,
        geocodingApi: GeocodingApi
    ): GeocodingService {
        return GeocodingService(context, geocodingApi, BuildConfig.MAPS_API_KEY)
    }
}
