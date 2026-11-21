package com.liskovsoft.youtubeapi.utils

import com.liskovsoft.googlecommon.common.converters.jsonpath.JsonPath
import com.liskovsoft.googlecommon.common.converters.jsonpath.WithJsonPath
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * SmartFin: Innertube navigation/resolve_url - turns any channel URL (youtube.com/@handle, /c/name, /user/name)
 * into the canonical channel id (UC...). Replaces scraping the channel web page, which returned 404 for
 * handles (the "@" was stripped) and depends on consent pages in the EU.
 */
@WithJsonPath
internal interface ResolveUrlApi {
    @Headers("Content-Type: application/json")
    @POST("https://www.youtube.com/youtubei/v1/navigation/resolve_url")
    fun resolveUrl(@Body query: String): Call<ResolvedUrl?>?
}

internal class ResolvedUrl {
    @JsonPath("$.endpoint.browseEndpoint.browseId")
    var browseId: String? = null
        private set
}
