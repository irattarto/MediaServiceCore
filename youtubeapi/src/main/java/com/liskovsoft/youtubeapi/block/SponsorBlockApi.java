package com.liskovsoft.youtubeapi.block;

import com.liskovsoft.googlecommon.common.converters.gson.WithGson;
import com.liskovsoft.youtubeapi.block.data.HashedVideoSegments;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Url;

/**
 * SmartFin: only the privacy-preserving hash-prefix endpoint is used. The full url (user-selectable server)
 * is built by {@link SponsorBlockService}.
 */
@WithGson
interface SponsorBlockApi {
    @GET
    Call<List<HashedVideoSegments>> getSegmentsByHashPrefix(@Url String url);
}
