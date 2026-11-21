package com.liskovsoft.youtubeapi.utils

import com.liskovsoft.googlecommon.common.helpers.RetrofitHelper
import com.liskovsoft.youtubeapi.common.helpers.PostDataHelper
import org.json.JSONObject

internal object UtilsService {
    private val mUtilsApi = RetrofitHelper.create(UtilsApi::class.java)
    private val mResolveUrlApi = RetrofitHelper.create(ResolveUrlApi::class.java)

    /**
     * Converts an alias ("@handle", or "@name" for legacy /c/ and /user/ links) into the canonical channel id (UC...).
     */
    @JvmStatic
    fun canonicalChannelId(channelId: String?): String? {
        if (channelId == null || !channelId.startsWith("@")) return channelId

        val name = channelId.substring(1)

        // SmartFin: resolve through the Innertube API. Handles first, then legacy custom urls.
        for (url in listOf("https://www.youtube.com/@$name", "https://www.youtube.com/c/$name", "https://www.youtube.com/user/$name")) {
            resolveUrl(url)?.let { return it }
        }

        // Old fallback: scrape the canonical link of the channel page
        val canonicalChannelId = mUtilsApi.canonicalChannelId(name)

        return RetrofitHelper.get(canonicalChannelId)?.channelId
    }

    private fun resolveUrl(url: String): String? {
        val query = PostDataHelper.createQueryWeb("\"url\":${JSONObject.quote(url)}")
        val browseId = RetrofitHelper.get(mResolveUrlApi.resolveUrl(query))?.browseId
        return if (browseId != null && browseId.startsWith("UC")) browseId else null
    }
}
