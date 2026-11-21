package com.liskovsoft.youtubeapi.block.data;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * One entry of the SponsorBlock hash-prefix response:
 * <code>GET /api/skipSegments/{sha256HashPrefix}</code> returns the segments of every video whose
 * SHA-256(videoID) starts with the prefix. The client picks its own video locally.<br/>
 * <a href="https://wiki.sponsor.ajay.app/w/API_Docs#GET_/api/skipSegments/:sha256HashPrefix">API docs</a>
 */
public class HashedVideoSegments {
    @SerializedName("videoID")
    public String videoId;

    @SerializedName("hash")
    public String hash;

    @SerializedName("segments")
    public List<Segment> segments;

    public static class Segment {
        @SerializedName("category")
        public String category;

        @SerializedName("actionType")
        public String actionType;

        /** [startSec, endSec] */
        @SerializedName("segment")
        public double[] segment;
    }
}
