package com.liskovsoft.youtubeapi.browse.v2

import com.liskovsoft.googlecommon.common.converters.gson.WithGson
import com.liskovsoft.googlecommon.common.helpers.DefaultHeaders
import com.liskovsoft.googlecommon.common.helpers.RetrofitHelper
import com.liskovsoft.youtubeapi.common.helpers.AppClient
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * SmartFin: channel title and avatar from YouTube's own browse API (replaces the YouTube Data API,
 * whose key was downloaded from upstream's GitHub at runtime).
 */
internal object ChannelMetadataService {
    private val mApi = RetrofitHelper.create(ChannelMetadataApi::class.java)

    data class ChannelInfo(val title: String?, val iconUrl: String?)

    /**
     * @return null on error or if the channel has no metadata
     */
    fun getChannelInfo(channelId: String?): ChannelInfo? {
        if (channelId.isNullOrEmpty()) {
            return null
        }

        val result = try {
            RetrofitHelper.get(mApi.getChannelMetadata(BrowseApiHelper.getChannelQuery(AppClient.WEB, channelId)))
        } catch (e: Exception) {
            null // network problem: the caller keeps what it has
        }

        val renderer = result?.metadata?.channelMetadataRenderer ?: return null
        val iconUrl = renderer.avatar?.thumbnails?.lastOrNull { !it?.url.isNullOrEmpty() }?.url

        return if (renderer.title == null && iconUrl == null) null else ChannelInfo(renderer.title, iconUrl)
    }
}

@WithGson
internal interface ChannelMetadataApi {
    @Headers(
        "Content-Type: application/json",
        "User-Agent: " + DefaultHeaders.USER_AGENT_WEB,
        "Referer: https://www.youtube.com/"
    )
    @POST("https://www.youtube.com/youtubei/v1/browse")
    fun getChannelMetadata(@Body browseQuery: String?): Call<ChannelMetadataResult?>
}

internal data class ChannelMetadataResult(val metadata: Metadata?) {
    data class Metadata(val channelMetadataRenderer: ChannelMetadataRenderer?)
    data class ChannelMetadataRenderer(val title: String?, val avatar: Avatar?)
    data class Avatar(val thumbnails: List<Thumbnail?>?)
    data class Thumbnail(val url: String?)
}
