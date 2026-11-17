package com.freesic.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.LruCache;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.ContentAvailability;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class YouTubeAudio {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final int MAX_SEARCH_RESULTS = 20;
    private static final long MAX_ARTWORK_BYTES = 5L * 1024L * 1024L;
    private static final int THUMB_CACHE_BYTES = 8 * 1024 * 1024;
    private static final Object INIT_LOCK = new Object();
    private static volatile boolean initialized;

    private static final LruCache<String, Bitmap> THUMB_CACHE = new LruCache<>(THUMB_CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getAllocationByteCount());
        }
    };

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private YouTubeAudio() {}

    public static final class SearchResult {
        public final String url, title, uploader, thumbnailUrl;
        public final long durationSec;

        public SearchResult(String url, String title, String uploader, String thumbnailUrl, long durationSec) {
            this.url = url == null ? "" : url;
            this.title = title == null ? "" : title;
            this.uploader = uploader == null ? "" : uploader;
            this.thumbnailUrl = thumbnailUrl == null ? "" : thumbnailUrl;
            this.durationSec = Math.max(0, durationSec);
        }
    }

    public static final class AudioOption {
        public final String formatName, mimeType, extension, url, qualityLabel;
        public final int bitrateKbps;
        public final long approxBytes;

        public AudioOption(String formatName, String mimeType, String extension, String url,
                           String qualityLabel, int bitrateKbps, long approxBytes) {
            this.formatName = formatName;
            this.mimeType = mimeType;
            this.extension = extension;
            this.url = url;
            this.qualityLabel = qualityLabel;
            this.bitrateKbps = bitrateKbps;
            this.approxBytes = approxBytes;
        }
    }

    public static final class VideoInfo {
        public final String url, title, uploader, thumbnailUrl;
        public final long durationSec;
        public final List<AudioOption> audioOptions;

        public VideoInfo(String url, String title, String uploader, String thumbnailUrl,
                         long durationSec, List<AudioOption> audioOptions) {
            this.url = url == null ? "" : url;
            this.title = title == null ? "" : title;
            this.uploader = uploader == null ? "" : uploader;
            this.thumbnailUrl = thumbnailUrl == null ? "" : thumbnailUrl;
            this.durationSec = Math.max(0, durationSec);
            this.audioOptions = Collections.unmodifiableList(new ArrayList<>(audioOptions));
        }
    }

    public interface ProgressListener {
        void onProgress(long downloadedBytes, long totalBytes);
    }

    public static boolean isYouTubeUrl(String input) {
        return MusicLogic.isYouTubeUrl(input);
    }

    public static Bitmap fetchThumbnailBitmap(String url, int maxSizePx) {
        if (url == null || url.trim().isEmpty()) return null;
        String cleanUrl = url.trim();
        int targetMax = Math.max(64, maxSizePx);
        String cacheKey = cleanUrl + "#" + targetMax;
        Bitmap cached = THUMB_CACHE.get(cacheKey);
        if (cached != null && !cached.isRecycled()) return cached;

        okhttp3.Request req = new okhttp3.Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", USER_AGENT)
                .build();
        try (okhttp3.Response res = HTTP.newCall(req).execute()) {
            ResponseBody body = res.body();
            if (!res.isSuccessful() || body == null) return null;
            long len = body.contentLength();
            if (len > MAX_ARTWORK_BYTES) return null;

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0L;
            try (InputStream in = body.byteStream()) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_ARTWORK_BYTES) return null;
                    out.write(buffer, 0, read);
                }
            }
            byte[] bytes = out.toByteArray();
            if (bytes.length == 0) return null;

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sampleSize = 1;
            while ((bounds.outWidth / (sampleSize * 2)) >= targetMax
                    || (bounds.outHeight / (sampleSize * 2)) >= targetMax) {
                sampleSize *= 2;
            }
            BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
            decodeOpts.inSampleSize = sampleSize;
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decodeOpts);
            if (bitmap == null) return null;

            int maxDim = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (maxDim > targetMax) {
                int scaledW = Math.max(1, bitmap.getWidth() * targetMax / maxDim);
                int scaledH = Math.max(1, bitmap.getHeight() * targetMax / maxDim);
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true);
                if (scaled != bitmap) bitmap.recycle();
                bitmap = scaled;
            }
            THUMB_CACHE.put(cacheKey, bitmap);
            return bitmap;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void ensureInit() {
        if (initialized) return;
        synchronized (INIT_LOCK) {
            if (initialized) return;
            NewPipe.init(new OkHttpDownloader(HTTP), new Localization("pt", "BR"), new ContentCountry("BR"));
            initialized = true;
        }
    }

    public static List<SearchResult> search(String query) throws IOException, ExtractionException {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Digite um termo ou link do YouTube.");
        }
        ensureInit();
        StreamingService youtube = ServiceList.YouTube;
        SearchInfo info = SearchInfo.getInfo(youtube, youtube.getSearchQHFactory().fromQuery(query.trim()));
        List<SearchResult> results = new ArrayList<>();
        for (InfoItem item : info.getRelatedItems()) {
            if (!(item instanceof StreamInfoItem)) continue;
            StreamInfoItem stream = (StreamInfoItem) item;
            StreamType type = stream.getStreamType();
            if (type == StreamType.LIVE_STREAM || type == StreamType.AUDIO_LIVE_STREAM) continue;
            if (stream.getDuration() <= 0 || stream.getContentAvailability() == ContentAvailability.UPCOMING) continue;
            String url = stream.getUrl();
            if (url == null || url.trim().isEmpty()) continue;
            results.add(new SearchResult(
                    url,
                    nonEmpty(stream.getName(), "Sem título"),
                    nonEmpty(stream.getUploaderName(), "YouTube"),
                    bestThumbnail(stream.getThumbnails()),
                    stream.getDuration()
            ));
            if (results.size() >= MAX_SEARCH_RESULTS) break;
        }
        return Collections.unmodifiableList(results);
    }

    public static VideoInfo inspect(String videoUrl) throws IOException, ExtractionException {
        String normalized = MusicLogic.normalizeYouTubeUrl(videoUrl);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Link do YouTube inválido.");
        }
        ensureInit();
        StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, normalized);
        StreamType type = info.getStreamType();
        if (type == StreamType.LIVE_STREAM || type == StreamType.AUDIO_LIVE_STREAM) {
            throw new IOException("Transmissões ao vivo não podem ser baixadas.");
        }
        ContentAvailability availability = info.getContentAvailability();
        if (availability == ContentAvailability.MEMBERSHIP
                || availability == ContentAvailability.PAID
                || availability == ContentAvailability.UPCOMING) {
            throw new IOException("Vídeo indisponível (exclusivo para membros, pago ou estreia).");
        }
        List<AudioStream> rawStreams = info.getAudioStreams();
        if (rawStreams == null || rawStreams.isEmpty()) {
            throw new IOException("Nenhuma faixa de áudio encontrada para este vídeo.");
        }
        boolean hasOriginal = false;
        for (AudioStream stream : rawStreams) {
            if (stream.getAudioTrackType() == AudioTrackType.ORIGINAL) {
                hasOriginal = true;
                break;
            }
        }
        long durationSec = Math.max(0, info.getDuration());
        Map<String, AudioOption> deduped = new LinkedHashMap<>();
        for (AudioStream stream : rawStreams) {
            if (!stream.isUrl() || stream.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP) continue;
            if (hasOriginal && stream.getAudioTrackType() != null
                    && stream.getAudioTrackType() != AudioTrackType.ORIGINAL) {
                continue;
            }
            String streamUrl = stream.getContent();
            if (streamUrl == null || streamUrl.trim().isEmpty()) continue;
            MediaFormat format = stream.getFormat();
            if (format == null) continue;

            FormatSpec spec = mapFormat(format);
            int bitrateKbps = resolveBitrateKbps(stream);
            ItagItem itag = stream.getItagItem();
            long contentLength = itag != null ? itag.getContentLength() : -1L;
            long approxBytes = contentLength > 0
                    ? contentLength
                    : (bitrateKbps > 0 && durationSec > 0 ? (long) bitrateKbps * 1000L * durationSec / 8L : -1L);

            String label = bitrateKbps > 0
                    ? spec.formatName + " · " + bitrateKbps + " kbps"
                    : spec.formatName;
            AudioOption candidate = new AudioOption(
                    spec.formatName, spec.mimeType, spec.extension, streamUrl, label, bitrateKbps, approxBytes);
            String key = spec.extension + ":" + bitrateKbps;
            AudioOption existing = deduped.get(key);
            if (existing == null || candidate.approxBytes > existing.approxBytes) {
                deduped.put(key, candidate);
            }
        }
        if (deduped.isEmpty()) {
            throw new IOException("Nenhuma faixa de áudio compatível encontrada (stream protegido por DRM ou indisponível).");
        }
        List<AudioOption> options = new ArrayList<>(deduped.values());
        options.sort(Comparator.comparingInt((AudioOption o) -> o.bitrateKbps).reversed()
                .thenComparingInt(o -> "m4a".equals(o.extension) ? 0 : 1));

        String canonicalUrl = nonEmpty(info.getUrl(), normalized);
        return new VideoInfo(
                canonicalUrl,
                nonEmpty(info.getName(), "Áudio do YouTube"),
                nonEmpty(info.getUploaderName(), "YouTube"),
                bestThumbnail(info.getThumbnails()),
                durationSec,
                options
        );
    }

    public static Track download(Context context, VideoInfo info, AudioOption option, ProgressListener listener)
            throws IOException {
        if (context == null || info == null || option == null || option.url == null || option.url.isEmpty()) {
            throw new IllegalArgumentException("Dados de download inválidos.");
        }
        Context app = context.getApplicationContext();
        String title = nonEmpty(info.title, "Áudio do YouTube");
        String artist = nonEmpty(info.uploader, "YouTube");
        String baseName = MusicLogic.sanitizeFilename(title);
        String ext = nonEmpty(option.extension, "m4a");
        String mime = nonEmpty(option.mimeType, "audio/mp4");

        String trackUri;
        if (Build.VERSION.SDK_INT >= 29) {
            trackUri = downloadMediaStore(app, option, title, artist, baseName, ext, mime,
                    info.durationSec, listener);
        } else {
            trackUri = downloadLegacyPublicFile(app, option, baseName, ext, mime, listener);
        }

        String artUri = downloadThumbnail(app, trackUri, info.thumbnailUrl);
        if (!artUri.isEmpty()) {
            new Store(app).prefs.edit().putString("art_override_" + trackUri, artUri).apply();
            Artwork.invalidate(trackUri);
        }

        long durationMs = info.durationSec > 0 ? info.durationSec * 1000L : probeDurationMs(app, trackUri);
        Track track = new Track(
                trackUri,
                title,
                artist,
                "YouTube",
                "Music/Freesic/",
                artUri,
                durationMs,
                System.currentTimeMillis() / 1000L,
                false
        );
        new Store(app).addImport(track);
        return track;
    }

    private static String downloadMediaStore(Context app, AudioOption option, String title, String artist,
                                             String baseName, String ext, String mime, long durationSec,
                                             ProgressListener listener) throws IOException {
        ContentResolver resolver = app.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, baseName + "." + ext);
        values.put(MediaStore.Audio.Media.MIME_TYPE, mime);
        values.put(MediaStore.Audio.Media.TITLE, title);
        values.put(MediaStore.Audio.Media.ARTIST, artist);
        values.put(MediaStore.Audio.Media.ALBUM, "YouTube");
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Freesic");
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);
        if (durationSec > 0) {
            values.put(MediaStore.Audio.Media.DURATION, durationSec * 1000L);
        }

        Uri itemUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        if (itemUri == null) {
            throw new IOException("Não foi possível criar o arquivo na pasta Músicas.");
        }
        try {
            try (OutputStream out = resolver.openOutputStream(itemUri)) {
                if (out == null) throw new IOException("Não foi possível gravar o arquivo de áudio.");
                streamToOutput(option.url, option.approxBytes, out, listener);
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Audio.Media.IS_PENDING, 0);
            resolver.update(itemUri, ready, null, null);
            return itemUri.toString();
        } catch (IOException | RuntimeException e) {
            try { resolver.delete(itemUri, null, null); } catch (Exception ignored) {}
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Falha ao salvar áudio: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("deprecation")
    private static String downloadLegacyPublicFile(Context app, AudioOption option, String baseName,
                                                   String ext, String mime, ProgressListener listener)
            throws IOException {
        File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        File freesicDir = new File(musicDir, "Freesic");
        if (!freesicDir.exists() && !freesicDir.mkdirs()) {
            throw new IOException("Permita o armazenamento para salvar músicas em Music/Freesic.");
        }
        File target = uniqueFile(freesicDir, baseName, ext);
        try {
            try (OutputStream out = new FileOutputStream(target)) {
                streamToOutput(option.url, option.approxBytes, out, listener);
            }
        } catch (IOException | RuntimeException e) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Falha ao salvar áudio: " + e.getMessage(), e);
        }

        Uri[] scanned = new Uri[1];
        CountDownLatch latch = new CountDownLatch(1);
        MediaScannerConnection.scanFile(
                app,
                new String[]{target.getAbsolutePath()},
                new String[]{mime},
                (path, uri) -> {
                    scanned[0] = uri;
                    latch.countDown();
                }
        );
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return scanned[0] != null ? scanned[0].toString() : Uri.fromFile(target).toString();
    }

    private static void streamToOutput(String url, long approxBytes, OutputStream out, ProgressListener listener)
            throws IOException {
        long downloaded = 0L;
        long total = approxBytes;
        int attempts = 0;

        while (attempts < 3) {
            attempts++;
            okhttp3.Request.Builder req = new okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept-Encoding", "identity");
            if (downloaded > 0) {
                req.header("Range", "bytes=" + downloaded + "-");
            }
            try (okhttp3.Response res = HTTP.newCall(req.build()).execute()) {
                if (downloaded > 0 && res.code() != 206) {
                    throw new IOException("Servidor não suportou retomada do download (HTTP " + res.code() + ").");
                }
                if (!res.isSuccessful()) {
                    throw new IOException("Erro ao baixar áudio (HTTP " + res.code() + ").");
                }
                ResponseBody body = res.body();
                if (body == null) {
                    throw new IOException("Resposta de áudio vazia.");
                }
                long contentLength = body.contentLength();
                if (downloaded == 0 && contentLength > 0) {
                    total = contentLength;
                } else if (downloaded > 0 && contentLength > 0 && total <= 0) {
                    total = downloaded + contentLength;
                }

                byte[] buffer = new byte[16384];
                try (InputStream in = body.byteStream()) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new IOException("Download cancelado.");
                        }
                        out.write(buffer, 0, read);
                        downloaded += read;
                        if (listener != null) {
                            listener.onProgress(downloaded, total);
                        }
                    }
                }
                out.flush();
                if (total <= 0 || downloaded >= total) {
                    if (listener != null) {
                        listener.onProgress(downloaded, total > 0 ? total : downloaded);
                    }
                    return;
                }
            } catch (IOException e) {
                if (Thread.currentThread().isInterrupted() || attempts >= 3 || downloaded == 0 || total <= 0) {
                    throw e;
                }
            }
        }
    }

    private static String downloadThumbnail(Context app, String trackUri, String thumbnailUrl) {
        if (thumbnailUrl == null || thumbnailUrl.trim().isEmpty()) return "";
        okhttp3.Request req = new okhttp3.Request.Builder()
                .url(thumbnailUrl)
                .header("User-Agent", USER_AGENT)
                .build();
        try (okhttp3.Response res = HTTP.newCall(req).execute()) {
            if (!res.isSuccessful() || res.body() == null) return "";
            long len = res.body().contentLength();
            if (len > MAX_ARTWORK_BYTES) return "";
            File dir = new File(app.getFilesDir(), "youtube-art");
            if (!dir.exists() && !dir.mkdirs()) return "";
            File outFile = new File(dir, sha256Hex(trackUri) + ".jpg");
            long written = 0L;
            byte[] buffer = new byte[8192];
            try (InputStream in = res.body().byteStream(); OutputStream out = new FileOutputStream(outFile)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    written += read;
                    if (written > MAX_ARTWORK_BYTES) {
                        //noinspection ResultOfMethodCallIgnored
                        outFile.delete();
                        return "";
                    }
                    out.write(buffer, 0, read);
                }
            }
            if (written == 0) {
                //noinspection ResultOfMethodCallIgnored
                outFile.delete();
                return "";
            }
            return Uri.fromFile(outFile).toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static long probeDurationMs(Context app, String trackUri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(app, Uri.parse(trackUri));
            String raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return raw == null ? 0L : Math.max(0L, Long.parseLong(raw));
        } catch (Exception ignored) {
            return 0L;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }

    private static File uniqueFile(File dir, String baseName, String ext) {
        File candidate = new File(dir, baseName + "." + ext);
        int index = 1;
        while (candidate.exists()) {
            candidate = new File(dir, baseName + " (" + index++ + ")." + ext);
        }
        return candidate;
    }

    private static int resolveBitrateKbps(AudioStream stream) {
        int avg = stream.getAverageBitrate();
        if (avg > 0) return avg;
        ItagItem itag = stream.getItagItem();
        if (itag != null && itag.getAverageBitrate() > 0) return itag.getAverageBitrate();
        int raw = stream.getBitrate();
        if (raw <= 0) return 0;
        return raw >= 1000 ? Math.round(raw / 1000f) : raw;
    }

    private static FormatSpec mapFormat(MediaFormat format) {
        switch (format) {
            case M4A:
            case MPEG_4:
                return new FormatSpec("M4A", "audio/mp4", "m4a");
            case WEBMA_OPUS:
            case OPUS:
                return new FormatSpec("Opus", "audio/webm", "webm");
            case WEBMA:
            case WEBM:
                return new FormatSpec("WebM", "audio/webm", "webm");
            case MP3:
                return new FormatSpec("MP3", "audio/mpeg", "mp3");
            case OGG:
                return new FormatSpec("OGG", "audio/ogg", "ogg");
            default:
                return new FormatSpec(
                        nonEmpty(format.getName(), "Áudio"),
                        nonEmpty(format.getMimeType(), "audio/*"),
                        nonEmpty(format.getSuffix(), "m4a")
                );
        }
    }

    private static String bestThumbnail(List<Image> images) {
        if (images == null || images.isEmpty()) return "";
        String bestUrl = "";
        int bestWidth = -1;
        for (Image img : images) {
            if (img == null || img.getUrl() == null || img.getUrl().trim().isEmpty()) continue;
            int w = img.getWidth();
            if (bestUrl.isEmpty() || w > bestWidth) {
                bestUrl = img.getUrl().trim();
                bestWidth = w;
            }
        }
        return bestUrl;
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String sha256Hex(String input) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >>> 4) & 15, 16))
                        .append(Character.forDigit(b & 15, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return Long.toHexString(input.hashCode());
        }
    }

    private static final class FormatSpec {
        final String formatName, mimeType, extension;

        FormatSpec(String formatName, String mimeType, String extension) {
            this.formatName = formatName;
            this.mimeType = mimeType;
            this.extension = extension;
        }
    }

    private static final class OkHttpDownloader extends Downloader {
        private final OkHttpClient client;

        OkHttpDownloader(OkHttpClient client) {
            this.client = client;
        }

        @Override
        public Response execute(Request request) throws IOException, ReCaptchaException {
            okhttp3.Request.Builder builder = new okhttp3.Request.Builder().url(request.url());
            Headers.Builder headersBuilder = new Headers.Builder();
            boolean hasUserAgent = false;
            String contentType = null;

            Map<String, List<String>> reqHeaders = request.headers();
            if (reqHeaders != null) {
                for (Map.Entry<String, List<String>> entry : reqHeaders.entrySet()) {
                    String name = entry.getKey();
                    if (name == null || entry.getValue() == null) continue;
                    if ("User-Agent".equalsIgnoreCase(name)) hasUserAgent = true;
                    if ("Content-Type".equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                        contentType = entry.getValue().get(0);
                    }
                    for (String val : entry.getValue()) {
                        if (val != null) headersBuilder.add(name, val);
                    }
                }
            }
            if (!hasUserAgent) {
                headersBuilder.add("User-Agent", USER_AGENT);
            }
            builder.headers(headersBuilder.build());

            String method = request.httpMethod() == null ? "GET" : request.httpMethod().toUpperCase(Locale.ROOT);
            byte[] data = request.dataToSend();
            RequestBody body = null;
            if (data != null) {
                MediaType mediaType = contentType != null ? MediaType.parse(contentType) : null;
                body = RequestBody.create(data, mediaType);
            } else if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
                MediaType mediaType = contentType != null ? MediaType.parse(contentType) : null;
                body = RequestBody.create(new byte[0], mediaType);
            }
            builder.method(method, body);

            try (okhttp3.Response response = client.newCall(builder.build()).execute()) {
                if (response.code() == 429) {
                    throw new ReCaptchaException("reCaptcha Challenge requested", request.url());
                }
                ResponseBody resBody = response.body();
                String bodyString = resBody != null ? resBody.string() : "";
                return new Response(
                        response.code(),
                        response.message(),
                        response.headers().toMultimap(),
                        bodyString,
                        response.request().url().toString()
                );
            }
        }
    }
}
