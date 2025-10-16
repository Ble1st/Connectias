package com.ble1st.connectias.network

import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.KotlinSerializationConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import kotlinx.serialization.json.Json

class UpdateChecker(
    private val retrofit: Retrofit
) {
    private val githubApi = retrofit.create(GitHubApi::class.java)
    
    suspend fun checkForUpdates(owner: String, repo: String): UpdateResult {
        return try {
            val releases = githubApi.getReleases(owner, repo)
            val latestRelease = releases.firstOrNull()
            
            if (latestRelease != null) {
                UpdateResult(
                    hasUpdate = true,
                    latestVersion = latestRelease.tagName,
                    releaseNotes = latestRelease.body,
                    downloadUrl = latestRelease.assets.firstOrNull()?.browserDownloadUrl,
                    publishedAt = latestRelease.publishedAt
                )
            } else {
                UpdateResult(
                    hasUpdate = false,
                    latestVersion = null,
                    releaseNotes = null,
                    downloadUrl = null,
                    publishedAt = null
                )
            }
        } catch (e: Exception) {
            UpdateResult(
                hasUpdate = false,
                latestVersion = null,
                releaseNotes = null,
                downloadUrl = null,
                publishedAt = null,
                error = e.message
            )
        }
    }
}

interface GitHubApi {
    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<GitHubRelease>
}

@Serializable
data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val assets: List<GitHubAsset>
)

@Serializable
data class GitHubAsset(
    val name: String,
    val browserDownloadUrl: String,
    val size: Long
)

data class UpdateResult(
    val hasUpdate: Boolean,
    val latestVersion: String?,
    val releaseNotes: String?,
    val downloadUrl: String?,
    val publishedAt: String?,
    val error: String? = null
)
