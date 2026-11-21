package com.liskovsoft.youtubeapi.block;

import androidx.annotation.Nullable;

import com.liskovsoft.googlecommon.common.helpers.RetrofitHelper;
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.youtubeapi.block.data.HashedVideoSegments;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import retrofit2.Response;

/**
 * SmartFin: privacy-preserving SponsorBlock client.
 * <ul>
 *     <li>Only the first {@link #HASH_PREFIX_LENGTH} hex characters of SHA-256(videoId) are sent
 *     (k-anonymity: the server answers for every video sharing that prefix and can't tell which one is played).</li>
 *     <li>The matching video is picked locally.</li>
 *     <li>Read-only: no user id, no submissions, no votes, no view counters.</li>
 *     <li>Goes through the shared client: no cookies, no YouTube/Google headers (those are added for YouTube/Google urls only).</li>
 *     <li>Responses are cached in memory per prefix for the app session.</li>
 * </ul>
 */
public class SponsorBlockService {
    public static final String DEFAULT_SERVER_URL = "https://sponsor.ajay.app";
    static final int HASH_PREFIX_LENGTH = 4;
    private static final int CACHE_SIZE = 64;
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static SponsorBlockService sInstance;
    private final SponsorBlockApi mApi;
    private final Map<String, List<HashedVideoSegments>> mCache = new LinkedHashMap<String, List<HashedVideoSegments>>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<HashedVideoSegments>> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private SponsorBlockService() {
        mApi = RetrofitHelper.create(SponsorBlockApi.class);
    }

    public static SponsorBlockService instance() {
        if (sInstance == null) {
            sInstance = new SponsorBlockService();
        }

        return sInstance;
    }

    /**
     * @return segments of the video, empty list when the server has none, null on a server error
     * @throws IllegalStateException on network errors (no internet, DNS...)
     */
    @Nullable
    public List<SponsorSegment> getSegments(String videoId, Set<String> categories) {
        if (videoId == null || !VIDEO_ID.matcher(videoId).matches() || categories == null || categories.isEmpty()) {
            return Collections.emptyList();
        }

        String url = buildUrl(getServerUrl(), hashPrefix(videoId), categories);

        if (url == null) {
            return null;
        }

        List<HashedVideoSegments> videos;

        synchronized (mCache) {
            videos = mCache.get(url);
        }

        if (videos == null) {
            Response<List<HashedVideoSegments>> response = RetrofitHelper.getResponse(mApi.getSegmentsByHashPrefix(url));

            if (response == null) {
                return null;
            }

            if (response.code() == 404) {
                videos = Collections.emptyList(); // no segments for any video with this prefix
            } else if (response.isSuccessful()) {
                videos = response.body() != null ? response.body() : Collections.emptyList();
            } else {
                return null; // server problem: don't cache
            }

            synchronized (mCache) {
                mCache.put(url, videos);
            }
        }

        return toSegments(findVideo(videos, videoId));
    }

    /**
     * Checks that a server answers the hash-prefix API (200 or 404 both mean "works").
     * @throws IllegalStateException on network errors
     */
    public boolean testServer(@Nullable String serverUrl) {
        String normalized = serverUrl == null || serverUrl.trim().isEmpty() ? DEFAULT_SERVER_URL : normalizeServerUrl(serverUrl);

        if (normalized == null) {
            return false;
        }

        // A random prefix: tells the server nothing about what is watched
        String url = buildUrl(normalized, hashPrefix(Long.toHexString(System.nanoTime())), Collections.singleton(SponsorSegment.CATEGORY_SPONSOR));
        Response<List<HashedVideoSegments>> response = url != null ? RetrofitHelper.getResponse(mApi.getSegmentsByHashPrefix(url)) : null;

        return response != null && (response.isSuccessful() || response.code() == 404);
    }

    public void clearCache() {
        synchronized (mCache) {
            mCache.clear();
        }
    }

    /**
     * The server chosen in settings, or the default one.
     */
    public static String getServerUrl() {
        String custom = GlobalPreferences.isInitialized() ? GlobalPreferences.sInstance.getContentBlockServerUrl() : null;
        String normalized = custom != null ? normalizeServerUrl(custom) : null;
        return normalized != null ? normalized : DEFAULT_SERVER_URL;
    }

    /**
     * Accepts https servers, and plain http only for hosts on the local network (self-hosted).
     * No user info, query or fragment. Trailing slashes are removed. Returns null if not acceptable.
     */
    @Nullable
    public static String normalizeServerUrl(@Nullable String input) {
        if (input == null) {
            return null;
        }

        String value = input.trim();

        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        if (value.isEmpty()) {
            return null;
        }

        URI uri;

        try {
            uri = new URI(value);
        } catch (Exception e) {
            return null;
        }

        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
        String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : null;

        if (host == null || host.isEmpty() || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return null;
        }

        if ("https".equals(scheme) || ("http".equals(scheme) && isLocalHost(host))) {
            return value;
        }

        return null;
    }

    static boolean isLocalHost(String host) {
        if (host.equals("localhost") || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa")
                || host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.")) {
            return true;
        }

        if (host.startsWith("172.")) {
            String[] parts = host.split("\\.");
            try {
                int second = parts.length > 1 ? Integer.parseInt(parts[1]) : -1;
                return second >= 16 && second <= 31;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return false;
    }

    /**
     * First {@link #HASH_PREFIX_LENGTH} lowercase hex chars of SHA-256(value).
     */
    static String hashPrefix(String value) {
        return sha256Hex(value).substring(0, HASH_PREFIX_LENGTH);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 is always present on Android
        }
    }

    @Nullable
    static String buildUrl(String serverUrl, String prefix, Set<String> categories) {
        HttpUrl base = HttpUrl.parse(serverUrl);

        if (base == null) {
            return null;
        }

        // Sorted: the same settings always give the same url (cache key)
        StringBuilder json = new StringBuilder("[");
        for (String category : new TreeSet<>(categories)) {
            if (json.length() > 1) {
                json.append(",");
            }
            json.append("\"").append(category.replace("\"", "")).append("\"");
        }
        json.append("]");

        return base.newBuilder()
                .addPathSegment("api")
                .addPathSegment("skipSegments")
                .addPathSegment(prefix)
                .addQueryParameter("categories", json.toString())
                .build()
                .toString();
    }

    @Nullable
    static HashedVideoSegments findVideo(@Nullable List<HashedVideoSegments> videos, String videoId) {
        if (videos == null) {
            return null;
        }

        for (HashedVideoSegments video : videos) {
            if (video != null && videoId.equals(video.videoId)) {
                return video;
            }
        }

        return null;
    }

    static List<SponsorSegment> toSegments(@Nullable HashedVideoSegments video) {
        List<SponsorSegment> result = new ArrayList<>();

        if (video == null || video.segments == null) {
            return result;
        }

        for (HashedVideoSegments.Segment segment : video.segments) {
            if (segment == null || segment.segment == null || segment.segment.length < 2) {
                continue;
            }

            result.add(new SegmentImpl(
                    (long) (segment.segment[0] * 1_000),
                    (long) (segment.segment[1] * 1_000),
                    segment.category,
                    segment.actionType));
        }

        return result;
    }

    private static class SegmentImpl implements SponsorSegment {
        private final long mStartMs;
        private final long mEndMs;
        private final String mCategory;
        private final String mAction;

        SegmentImpl(long startMs, long endMs, String category, String action) {
            mStartMs = startMs;
            mEndMs = endMs;
            mCategory = category;
            mAction = action;
        }

        @Override
        public long getStartMs() {
            return mStartMs;
        }

        @Override
        public long getEndMs() {
            return mEndMs;
        }

        @Override
        public String getCategory() {
            return mCategory;
        }

        @Override
        public String getAction() {
            return mAction;
        }
    }
}
