package com.dcompress;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.view.Gravity;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.media.MediaExtractor;
import android.media.MediaMuxer;
import android.media.MediaFormat;

public class MainActivity extends Activity {
    private static final int PICK_PHOTO = 101, PICK_VIDEO = 102;
    private TextView status, fileInfo;
    private Uri selectedUri;
    private String selectedName = "media";
    private boolean selectedVideo = false;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        createScreen();
    }

    private void createScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(24, 28, 24, 28);
        page.setBackgroundColor(0xFFF5F7FB);

        TextView title = new TextView(this);
        title.setText("D-Compress");
        title.setTextSize(30);
        title.setTextColor(0xFF14213D);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);

        TextView tagline = new TextView(this);
        tagline.setText("Lossless Media Tools  •  No Ads");
        tagline.setTextSize(15);
        tagline.setTextColor(0xFF526078);
        tagline.setGravity(Gravity.CENTER);

        TextView note = new TextView(this);
        note.setText("Preserve quality. Keep your original files safe. Smaller output is not guaranteed for every file.");
        note.setTextSize(14);
        note.setTextColor(0xFF344054);
        note.setPadding(4, 18, 4, 18);
        note.setGravity(Gravity.CENTER);

        Button choosePhoto = button("Choose Photo");
        Button chooseVideo = button("Choose Video");
        Button process = button("Run Lossless Process");

        fileInfo = new TextView(this);
        fileInfo.setText("No file selected");
        fileInfo.setTextColor(0xFF202938);
        fileInfo.setTextSize(14);
        fileInfo.setPadding(8, 18, 8, 12);

        status = new TextView(this);
        status.setText("Choose a photo or video to begin.");
        status.setTextColor(0xFF202938);
        status.setTextSize(15);
        status.setPadding(8, 12, 8, 18);

        choosePhoto.setOnClickListener(v -> choose("image/*", PICK_PHOTO));
        chooseVideo.setOnClickListener(v -> choose("video/*", PICK_VIDEO));
        process.setOnClickListener(v -> startProcessing());

        page.addView(title, match());
        page.addView(tagline, match());
        page.addView(note, match());
        page.addView(choosePhoto, match());
        page.addView(chooseVideo, match());
        page.addView(fileInfo, match());
        page.addView(process, match());
        page.addView(status, match());
        scroll.addView(page);
        setContentView(scroll);
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        return b;
    }
    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private void choose(String mime, int request) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType(mime);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, request);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        selectedUri = data.getData();
        selectedVideo = request == PICK_VIDEO;
        selectedName = getDisplayName(selectedUri);
        long size = getSize(selectedUri);
        fileInfo.setText("Selected: " + selectedName + "\nSize: " + human(size) +
                "\nType: " + (selectedVideo ? "Video" : "Photo"));
        status.setText("Ready. Your original file will not be modified.");
    }

    private String getDisplayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) {}
        return "selected_media";
    }

    private long getSize(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.SIZE);
                if (i >= 0 && !c.isNull(i)) return c.getLong(i);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private void startProcessing() {
        if (selectedUri == null) {
            status.setText("Please choose a file first.");
            return;
        }
        status.setText("Working… Please keep the app open.");
        final Uri input = selectedUri;
        final String name = selectedName;
        final boolean video = selectedVideo;
        worker.execute(() -> {
            String result;
            try {
                result = video ? processVideo(input, name) : processPhoto(input, name);
            } catch (Exception e) {
                result = "Could not process this file: " + e.getMessage();
            }
            final String message = result;
            runOnUiThread(() -> status.setText(message));
        });
    }

    private String processPhoto(Uri uri, String name) throws Exception {
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".png")) {
            return "Safe mode: this build only re-encodes PNG photos. This file was not changed. JPEG and other formats are left untouched to avoid quality or metadata changes.";
        }
        Bitmap bitmap;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot open photo");
            bitmap = BitmapFactory.decodeStream(in);
        }
        if (bitmap == null) throw new IOException("Unsupported or unreadable PNG");
        File temp = File.createTempFile("dcompress_", ".png", getCacheDir());
        try (FileOutputStream out = new FileOutputStream(temp)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                throw new IOException("PNG encoding failed");
        }
        bitmap.recycle();
        long before = getSize(uri);
        long after = temp.length();
        if (before >= 0 && after >= before) {
            temp.delete();
            return "No size reduction found. Original kept unchanged; no larger output was saved.";
        }
        Uri saved = saveToPictures(temp, "DCompress_" + safeName(name), "image/png");
        temp.delete();
        return "Lossless PNG output saved to Pictures/D-Compress.\nBefore: " + human(before) +
                "\nAfter: " + human(after) + "\nSaved: " + (saved != null ? "Yes" : "No");
    }

    private String processVideo(Uri uri, String name) throws Exception {
        // Remux encoded tracks without decoding/re-encoding. This preserves encoded audio/video streams.
        File temp = File.createTempFile("dcompress_", ".mp4", getCacheDir());
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean started = false;
        try {
            extractor.setDataSource(this, uri, null);
            muxer = new MediaMuxer(temp.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            Map<Integer,Integer> trackMap = new HashMap<>();
            int count = extractor.getTrackCount();
            for (int i = 0; i < count; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && (mime.startsWith("video/") || mime.startsWith("audio/"))) {
                    int outTrack = muxer.addTrack(format);
                    trackMap.put(i, outTrack);
                }
            }
            if (trackMap.isEmpty()) throw new IOException("No supported audio/video tracks found");
            muxer.start();
            started = true;
            android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();
            int max = 1024 * 1024;
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocateDirect(max);
            for (Map.Entry<Integer,Integer> entry : trackMap.entrySet()) {
                extractor.selectTrack(entry.getKey());
            }
            boolean[] finished = new boolean[count];
            while (true) {
                int track = extractor.getSampleTrackIndex();
                if (track < 0) break;
                Integer outTrack = trackMap.get(track);
                if (outTrack == null) {
                    extractor.advance();
                    continue;
                }
                buffer.clear();
                int size = extractor.readSampleData(buffer, max);
                if (size < 0) break;
                info.set(0, size, extractor.getSampleTime(), extractor.getSampleFlags());
                muxer.writeSampleData(outTrack, buffer, info);
                extractor.advance();
            }
            muxer.stop();
            started = false;
        } finally {
            try { extractor.release(); } catch (Exception ignored) {}
            if (muxer != null) {
                if (started) try { muxer.stop(); } catch (Exception ignored) {}
                try { muxer.release(); } catch (Exception ignored) {}
            }
        }
        long before = getSize(uri), after = temp.length();
        if (before >= 0 && after >= before) {
            temp.delete();
            return "Video remux did not make the file smaller. Original kept unchanged; no larger output was saved.";
        }
        Uri saved = saveToMovies(temp, "DCompress_" + stripExtension(safeName(name)) + ".mp4");
        temp.delete();
        return "Video remux complete (no re-encoding).\nBefore: " + human(before) +
                "\nAfter: " + human(after) + "\nSaved in Movies/D-Compress: " + (saved != null ? "Yes" : "No");
    }

    private Uri saveToPictures(File source, String name, String mime) throws Exception {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        cv.put(MediaStore.Images.Media.MIME_TYPE, mime);
        if (Build.VERSION.SDK_INT >= 29)
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/D-Compress");
        Uri out = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
        if (out == null) throw new IOException("Cannot create output image");
        copyFile(source, out);
        return out;
    }

    private Uri saveToMovies(File source, String name) throws Exception {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Video.Media.DISPLAY_NAME, name);
        cv.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= 29)
            cv.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/D-Compress");
        Uri out = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
        if (out == null) throw new IOException("Cannot create output video");
        copyFile(source, out);
        return out;
    }

    private void copyFile(File src, Uri dest) throws Exception {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = getContentResolver().openOutputStream(dest)) {
            if (out == null) throw new IOException("Cannot open output");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
        }
    }

    private String safeName(String name) { return name.replaceAll("[^A-Za-z0-9._-]", "_"); }
    private String stripExtension(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }
    private String human(long bytes) {
        if (bytes < 0) return "Unknown";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L*1024) return String.format(Locale.US, "%.1f KB", bytes/1024.0);
        return String.format(Locale.US, "%.2f MB", bytes/(1024.0*1024));
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        worker.shutdown();
    }
}
