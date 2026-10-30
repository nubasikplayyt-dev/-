package app.investwatch.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * GitHub REST API: чтение файлов репозитория данных и запись одного
 * файла — config/favorites.json.
 *
 * Содержимое читается с `Accept: application/vnd.github.raw`, список файлов
 * и их sha берётся одним запросом через Git Trees API: так на сотне бумаг
 * не уходит сотня запросов, а неизменившиеся файлы вообще не скачиваются.
 */
interface GitHubApi {

    @GET("repos/{owner}/{repo}/git/trees/{ref}")
    suspend fun tree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String,
        @Query("recursive") recursive: Int = 1,
        @Header("If-None-Match") etag: String? = null,
    ): Response<TreeDto>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun raw(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Query("ref") ref: String,
        @Header("Accept") accept: String = RAW,
        @Header("If-None-Match") etag: String? = null,
    ): Response<ResponseBody>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun metadata(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Query("ref") ref: String,
    ): Response<ContentDto>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun put(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body body: PutFileDto,
    ): Response<ContentResponseDto>

    companion object {
        const val BASE_URL = "https://api.github.com/"
        const val RAW = "application/vnd.github.raw"
    }
}

@Serializable
data class TreeDto(
    val sha: String? = null,
    val tree: List<TreeEntryDto> = emptyList(),
    val truncated: Boolean = false,
)

@Serializable
data class TreeEntryDto(
    val path: String,
    val type: String,
    val sha: String? = null,
    val size: Long? = null,
)

@Serializable
data class ContentDto(
    val path: String? = null,
    val sha: String? = null,
    val content: String? = null,
    val encoding: String? = null,
)

@Serializable
data class PutFileDto(
    val message: String,
    val content: String,
    val sha: String? = null,
    val branch: String? = null,
)

@Serializable
data class ContentResponseDto(
    @SerialName("content") val content: ContentDto? = null,
)
