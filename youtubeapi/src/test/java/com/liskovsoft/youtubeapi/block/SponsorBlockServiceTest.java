package com.liskovsoft.youtubeapi.block;

import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment;
import com.liskovsoft.youtubeapi.block.data.HashedVideoSegments;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Offline checks of the privacy-preserving SponsorBlock lookup (no network).
 */
public class SponsorBlockServiceTest {
    private static final String VIDEO_ID = "dQw4w9WgXcQ";

    @Test
    public void hashPrefixIsFirstFourHexCharsOfSha256() {
        // python: hashlib.sha256(b'dQw4w9WgXcQ').hexdigest()
        assertEquals("5f6b0b4e201f2a7e66927abb5cadeec81624dcc8efe6644b78aa182213f653a2", SponsorBlockService.sha256Hex(VIDEO_ID));
        assertEquals("5f6b", SponsorBlockService.hashPrefix(VIDEO_ID));
    }

    @Test
    public void urlNeverContainsTheVideoId() {
        String url = SponsorBlockService.buildUrl(SponsorBlockService.DEFAULT_SERVER_URL, SponsorBlockService.hashPrefix(VIDEO_ID),
                new LinkedHashSet<>(Arrays.asList("sponsor", "intro")));

        assertFalse(url.contains(VIDEO_ID));
        assertTrue(url.startsWith("https://sponsor.ajay.app/api/skipSegments/5f6b?categories="));
        // categories sorted -> stable cache key
        assertTrue(url.endsWith("%5B%22intro%22,%22sponsor%22%5D") || url.endsWith("%5B%22intro%22%2C%22sponsor%22%5D"));
    }

    @Test
    public void customServerKeepsItsPath() {
        String url = SponsorBlockService.buildUrl("https://example.org/sb", "abcd", Collections.singleton("sponsor"));
        assertTrue(url.startsWith("https://example.org/sb/api/skipSegments/abcd?"));
    }

    @Test
    public void serverUrlValidation() {
        assertEquals("https://sponsor.ajay.app", SponsorBlockService.normalizeServerUrl(" https://sponsor.ajay.app/ "));
        assertEquals("https://mirror.example/sb", SponsorBlockService.normalizeServerUrl("https://mirror.example/sb//"));
        assertEquals("http://192.168.1.20:8080", SponsorBlockService.normalizeServerUrl("http://192.168.1.20:8080"));
        assertEquals("http://sponsorblock.local", SponsorBlockService.normalizeServerUrl("http://sponsorblock.local"));
        assertEquals("http://172.20.0.5", SponsorBlockService.normalizeServerUrl("http://172.20.0.5"));

        assertNull(SponsorBlockService.normalizeServerUrl("http://sponsor.ajay.app")); // plain http on the internet
        assertNull(SponsorBlockService.normalizeServerUrl("http://172.32.0.5"));
        assertNull(SponsorBlockService.normalizeServerUrl("ftp://example.org"));
        assertNull(SponsorBlockService.normalizeServerUrl("https://user:pass@example.org"));
        assertNull(SponsorBlockService.normalizeServerUrl("https://example.org/?x=1"));
        assertNull(SponsorBlockService.normalizeServerUrl("sponsor.ajay.app"));
        assertNull(SponsorBlockService.normalizeServerUrl(""));
        assertNull(SponsorBlockService.normalizeServerUrl(null));
    }

    @Test
    public void onlyTheRequestedVideoIsPickedFromTheResponse() {
        HashedVideoSegments other = video("aaaaaaaaaaa", 1, 2);
        HashedVideoSegments mine = video(VIDEO_ID, 10.5, 20.25);

        List<SponsorSegment> segments = SponsorBlockService.toSegments(SponsorBlockService.findVideo(Arrays.asList(other, mine), VIDEO_ID));

        assertEquals(1, segments.size());
        assertEquals(10_500, segments.get(0).getStartMs());
        assertEquals(20_250, segments.get(0).getEndMs());
        assertEquals("sponsor", segments.get(0).getCategory());

        assertTrue(SponsorBlockService.toSegments(SponsorBlockService.findVideo(Collections.singletonList(other), VIDEO_ID)).isEmpty());
    }

    private static HashedVideoSegments video(String videoId, double start, double end) {
        HashedVideoSegments.Segment segment = new HashedVideoSegments.Segment();
        segment.category = "sponsor";
        segment.actionType = "skip";
        segment.segment = new double[] {start, end};

        HashedVideoSegments video = new HashedVideoSegments();
        video.videoId = videoId;
        video.segments = Collections.singletonList(segment);
        return video;
    }
}
