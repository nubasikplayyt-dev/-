package app.investwatch.di

import android.content.Context
import app.investwatch.data.SecureStore
import app.investwatch.data.db.AppDatabase
import app.investwatch.data.net.GitHubApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun context(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Provides
    @Singleton
    fun okHttp(secureStore: SecureStore): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            // Токен подставляем только запросам к GitHub: источникам цен он не нужен.
            val builder = request.newBuilder()
            if (request.url.host == "api.github.com") {
                secureStore.read(SecureStore.GITHUB_TOKEN)?.takeIf { it.isNotBlank() }?.let {
                    builder.header("Authorization", "Bearer $it")
                }
                builder.header("X-GitHub-Api-Version", "2022-11-28")
            }
            builder.header("User-Agent", "InvestWatch Android")
            chain.proceed(builder.build())
        }
        .build()

    @Provides
    @Singleton
    fun gitHubApi(client: OkHttpClient, json: Json): GitHubApi = Retrofit.Builder()
        .baseUrl(GitHubApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GitHubApi::class.java)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase = AppDatabase.create(context)
}
