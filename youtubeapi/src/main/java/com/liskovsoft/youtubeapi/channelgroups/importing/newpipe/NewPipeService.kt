package com.liskovsoft.youtubeapi.channelgroups.importing.newpipe

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup
import com.liskovsoft.youtubeapi.browse.v2.ChannelMetadataService
import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup.Item
import com.liskovsoft.youtubeapi.channelgroups.importing.GroupImportService
import com.liskovsoft.youtubeapi.channelgroups.importing.newpipe.gen.NewPipeSubscriptionsGroup
import com.liskovsoft.youtubeapi.channelgroups.models.ItemGroupImpl
import com.liskovsoft.youtubeapi.channelgroups.models.ItemImpl
import com.liskovsoft.googlecommon.common.helpers.YouTubeHelper
import com.liskovsoft.youtubeapi.app.nsigsolver.common.YouTubeInfoExtractor
import java.io.File

internal object NewPipeService: GroupImportService {
    override fun importGroups(url: Uri): List<ItemGroup>? {
        val grayJayContent = YouTubeInfoExtractor.downloadWebpageSilent(url.toString()) ?: return null

        return parseGroups(grayJayContent)
    }

    override fun importGroups(file: File): List<ItemGroup>? {
        return parseGroups(file.readText())
    }

    private fun parseGroups(newPipeContent: String): List<ItemGroup>? {
        val gson = Gson()
        val myType = object : TypeToken<NewPipeSubscriptionsGroup>() {}.type

        val response: NewPipeSubscriptionsGroup = try {
            gson.fromJson(newPipeContent, myType)
        } catch (e: JsonSyntaxException) {
            return null
        }

        val result = mutableListOf<ItemGroup>()

        val items: MutableList<Item> = mutableListOf()

        // channel url: https://www.youtube.com/channel/UCbWcXB0PoqOsAvAdfzWMf0w
        response.subscriptions?.forEach { items.add(ItemImpl(channelId = YouTubeHelper.extractChannelId(Uri.parse(it.url)), title = it.name)) }

        // SmartFin: channel icons (and current titles) from YouTube's own browse API. Upstream used the YouTube Data API
        // with a key downloaded from upstream's GitHub at runtime. The names from the NewPipe export are the fallback.
        val newItems: MutableList<Item> = items.map { item ->
            val info = ChannelMetadataService.getChannelInfo(item.channelId)
            ItemImpl(item.channelId, info?.title ?: item.title, info?.iconUrl ?: item.iconUrl)
        }.toMutableList()

        result.add(ItemGroupImpl(title = NewPipeSubscriptionsGroup::subscriptions.name, items = newItems))

        return result
    }
}