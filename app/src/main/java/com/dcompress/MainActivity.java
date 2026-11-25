
ackage com.dcompress;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.content.ContentValues;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaExtractor;
import android.media.MediaMuxer;
import android.view.Gravity;
import android.widget.*;
import android.view.View;
import android.os.Build;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int PICK_PHOTO = 101;
    private static final int PICK_VIDEO = 102;
    private static final int PICK_ZIP = 103;
    private static final int PICK_ZIP_FILES = 104;

    private TextView status, fileInfo;
    private Uri selectedUri;
    private String selectedName = "media";
    private boolean selectedVideo = false;
    private int targetKB = 500;

    private ArrayList<Uri> zipUris = new ArrayList<>();
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createScreen();
    }

    private void createScreen() {
        ScrollView scroll = new ScrollView(this);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(22, 28, 22, 28);
        page.setBackgroundColor(Color.rgb(244, 247, 252));

        TextView title = new TextView(this);
        title.setText("D-COMPRESS");
        title.setTextSize(30);
        title.setTextColor(Color.rgb(23, 37, 84));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, Typeface.BOLD);

        TextView subtitle = new TextView(this);
        subtitle.setText("SMART FILE COMPRESSION");
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.GRAY);
        subtitle.setGravity(Gravity.CENTER);

        TextView intro = new TextView(this);
        intro.setText("Compress files. Keep originals safe.");
        intro.setTextSize(15);
        intro.setTextColor(Color.DKGRAY);
        intro.setGravity(Gravity.CENTER);
        intro.setPadding(0, 18, 0, 24);

        fileInfo = new TextView(this);
        fileInfo.setText("No file selected");
        fileInfo.setTextColor(Color.DKGRAY);
        fileInfo.setTextSize(14);
        fileInfo.setPadding(8, 16, 8, 16);

        status = new TextView(this);
        status.setText("Choose a tool to begin.");
        status.setTextColor(Color.rgb(30, 64, 100));
        status.setTextSize(14);
        status.setPadding(8, 16, 8, 16);

        page.addView(title, match());
        page.addView(subtitle, match());
        page.addView(intro, match());

        heading(page, "PHOTO TOOLS");
        addButton(page, "📷  Choose Photo",
                v -> choose("image/*", PICK_PHOTO));
        addButton(page, "⚡  Smart Photo Compression",
                v -> startPhoto(true));
        addButton(page, "🔒  Lossless PNG Re-encode",
                v -> startPhoto(false));

        heading(page, "VIDEO TOOLS");
        addButton(page, "🎬  Choose Video",
                v -> choose("video/*", PICK_VIDEO));
        addButton(page, "🔒  Lossless Video Remux",
                v -> startVideo());

        heading(page, "TARGET SIZE");
        addButton(page, "500 KB",
                v -> setTarget(500));
        addButton(page, "1 MB",
                v -> setTarget(1024));
        addButton(page, "200 KB",
                v -> setTarget(200));
        addButton(page, "100 KB",
                v -> setTarget(100));
        addButton(page, "Custom Target (KB)",
                v -> customTarget());

        heading(page, "ZIP TOOLS");
        addButton(page, "📦  Create ZIP",
                v -> chooseZipFiles());
        addButton(page, "📂  Extract ZIP",
                v -> choose("application/zip", PICK_ZIP));

        page.addView(fileInfo, match());
        page.addView(status, match());

        scroll.addView(page);
        setContentView(scroll);
    }

    private void heading(LinearLayout page, String text) {
        TextView h = new TextView(this);
        h.setText(text);
        h.setTextSize(13);
        h.setTextColor(Color.rgb(100, 116, 139));
        h.setTypeface(null, Typeface.BOLD);
        h.setPadding(4, 22, 4, 8);
        page.addView(h, match());
    }

    private void addButton(LinearLayout page, String text,
                           View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(37, 99, 235));
        bg.setCornerRadius(22);
        b.setBackground(bg);

        b.setOnClickListener(listener);

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 5, 0, 5);
        page.addView(b, p);
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private void choose(String type, int request) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType(type);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, request);
    }

    private void chooseZipFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, PICK_ZIP_FILES);
    }

    @Override
    protected void onActivityResult(int requestCode,
                                   int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == PICK_ZIP_FILES) {
            zipUris.clear();

            if (data.getClipData() != null) {
                for (int x = 0; x < data.getClipData().getItemCount(); x++) {
                    zipUris.add(data.getClipData().getItemAt(x).getUri());
                }
            } else if (data.getData() != null) {
                zipUris.add(data.getData());
            }

            fileInfo.setText(zipUris.size() + " files selected for ZIP");
            createZip();
            return;
        }

        selectedUri = data.getData();
        if (selectedUri == null) return;

        getContentResolver().takePersistableUriPermission(
                selectedUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        );

        selectedVideo = requestCode == PICK_VIDEO
                || requestCode == PICK_ZIP;

        selectedName = getDisplayName(selectedUri);

        fileInfo.setText(
                "Selected: " + selectedName +
                "\nSize: " + human(getSize(selectedUri))
        );

        if (requestCode == PICK_ZIP) {
            extractZip();
        }
    }

    private String getDisplayName(Uri uri) {
        String name = "media";

        try (Cursor c = getContentResolver().query(
                uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = c.getString(index);
            }
        } catch (Exception ignored) {}

        return name;
    }

    private long getSize(Uri uri) {
        try (Cursor c = getContentResolver().query(
                uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int index = c.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0) return c.getLong(index);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private void setTarget(int kb) {
        targetKB = kb;
        status.setText("Target size set to " + kb + " KB");
    }

    private void customTarget() {
        EditText input = new EditText(this);
        input.setInputType(2);
        input.setHint("Enter target size in KB");

        new android.app.AlertDialog.Builder(this)
                .setTitle("Custom Target")
                .setView(input)
                .setPositiveButton("Set", (d, w) -> {
                    try {
                        int value = Integer.parseInt(
                                input.getText().toString());
                        if (value > 0) setTarget(value);
                        else status.setText("Enter a valid size.");
                    } catch (Exception e) {
                        status.setText("Invalid target size.");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startPhoto(boolean smart) {
        if (selectedUri == null || selectedVideo) {
            status.setText("Please choose a photo first.");
            return;
        }

        final Uri uri = selectedUri;
        final String name = selectedName;
        final int target = targetKB;

        status.setText("Processing photo...");

        worker.execute(() -> {
            try {
                processPhoto(uri, name, smart, target);
            } catch (Exception e) {
                showStatus("Photo error: " + e.getMessage());
            }
        });
    }

    private void processPhoto(Uri uri, String name,
                              boolean smart, int target) throws Exception {

        byte[] original;

        try (InputStream in = getContentResolver().openInputStream(uri)) {
            original = readAll(in);
        }

        Bitmap bitmap = BitmapFactory.decodeByteArray(
                original, 0, original.length);

        if (bitmap == null) {
            showStatus("This image format cannot be decoded.");
            return;
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        if (!smart) {
            String lower = name.toLowerCase(Locale.ROOT);

            if (!lower.endsWith(".png")) {
                bitmap.recycle();
                showStatus("Lossless PNG mode supports PNG files only.");
                return;
            }

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } else {
            int quality = 90;
            byte[] result = new byte[0];
            long limit = target * 1024L;

            while (quality >= 20) {
                output.reset();
                bitmap.compress(Bitmap.CompressFormat.JPEG,
                        quality, output);
                result = output.toByteArray();

                if (result.length <= limit) break;
                quality -= 5;
            }

            if (result.length == 0) {
                bitmap.recycle();
                showStatus("Compression failed.");
                return;
            }

            output.reset();
            output.write(result);
        }

        bitmap.recycle();

        byte[] compressed = output.toByteArray();

        if (compressed.length >= original.length) {
            showStatus("No size reduction. Original kept safe.\nOriginal: "
                    + human(original.length)
                    + "\nResult: " + human(compressed.length));
            return;
        }

        String outputName = smart
                ? stripExtension(name) + "_compressed.jpg"
                : stripExtension(name) + "_lossless.png";

        Uri saved = saveMedia(compressed, outputName,
                smart ? "image/jpeg" : "image/png",
                "Pictures/D-Compress");

        showStatus("Photo saved!\n" +
                "Original: " + human(original.length) +
                "\nNew size: " + human(compressed.length) +
                "\nSaved: " + human(original.length - compressed.length) +
                "\nTarget: " + target + " KB" +
                "\nLocation: Pictures/D-Compress");
    }

    private void startVideo() {
        if (selectedUri == null || !selectedVideo) {
            status.setText("Please choose a video first.");
            return;
        }

        Uri uri = selectedUri;
        String name = selectedName;

        status.setText("Processing video...");

        worker.execute(() -> {
            try {
                processVideo(uri, name);
            } catch (Exception e) {
                showStatus("Video error: " + e.getMessage());
            }
        });
    }

    private void processVideo(Uri uri, String name) throws Exception {
        File output = new File(getCacheDir(),
                "video_" + System.currentTimeMillis() + ".mp4");

        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(this, uri, null);

        MediaMuxer muxer = new MediaMuxer(
                output.getAbsolutePath(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        int[] trackMap = new int[extractor.getTrackCount()];
        Arrays.fill(trackMap, -1);

        int tracks = 0;

        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);

            if (mime != null &&
                    (mime.startsWith("video/") ||
                     mime.startsWith("audio/"))) {
                extractor.selectTrack(i);
                trackMap[i] = muxer.addTrack(format);
                tracks++;
            }
        }

        if (tracks == 0) {
            extractor.release();
            muxer.release();
            throw new Exception("No compatible audio/video tracks.");
        }

        muxer.start();

        android.media.MediaCodec.BufferInfo info =
                new android.media.MediaCodec.BufferInfo();

        java.nio.ByteBuffer buffer =
                java.nio.ByteBuffer.allocate(4 * 1024 * 1024);

        while (true) {
            int track = extractor.getSampleTrackIndex();
            if (track < 0) break;

            buffer.clear();
            int size = extractor.readSampleData(buffer, 0);

            if (size < 0) break;

            info.offset = 0;
            info.size = size;
            info.presentationTimeUs = extractor.getSampleTime();
            info.flags = extractor.getSampleFlags();

            if (trackMap[track] >= 0) {
                muxer.writeSampleData(trackMap[track], buffer, info);
            }

            extractor.advance();
        }

        muxer.stop();
        muxer.release();
        extractor.release();

        long originalSize = getSize(uri);
        long newSize = output.length();

        if (newSize < originalSize) {
            byte[] bytes = readFile(output);

            saveMedia(bytes,
                    stripExtension(name) + "_remuxed.mp4",
                    "video/mp4",
                    "Movies/D-Compress");

            showStatus("Video saved!\nOriginal: " +
                    human(originalSize) +
                    "\nNew: " + human(newSize) +
                    "\nLocation: Movies/D-Compress");
        } else {
            showStatus("Video was not smaller. Original kept safe.\n" +
                    "Original: " + human(originalSize) +
                    "\nRemuxed: " + human(newSize));
        }

        output.delete();
    }

    private void createZip() {
        if (zipUris.isEmpty()) {
            status.setText("Select files first.");
            return;
        }

        status.setText("Creating ZIP...");

        ArrayList<Uri> files = new ArrayList<>(zipUris);

        worker.execute(() -> {
            File zip = new File(getCacheDir(),
                    "DCompress_" + System.currentTimeMillis() + ".zip");

            try (ZipOutputStream zos =
                         new ZipOutputStream(new FileOutputStream(zip))) {

                byte[] buffer = new byte[8192];

                for (Uri uri : files) {
                    String name = safeName(getDisplayName(uri));

                    zos.putNextEntry(new ZipEntry(name));

                    try (InputStream in =
                                 getContentResolver().openInputStream(uri)) {
                        int count;
                        while ((count = in.read(buffer)) > 0) {
                            zos.write(buffer, 0, count);
                        }
                    }

                    zos.closeEntry();
                }

                byte[] bytes = readFile(zip);

                saveMedia(bytes,
                        "DCompress_" + System.currentTimeMillis() + ".zip",
                        "application/zip",
                        "Download");

                showStatus("ZIP created successfully.\n" +
                        "Size: " + human(zip.length()) +
                        "\nSaved in Downloads.");
            } catch (Exception e) {
                showStatus("ZIP error: " + e.getMessage());
            } finally {
                zip.delete();
            }
        });
    }

    private void extractZip() {
        if (selectedUri == null) return;

        Uri uri = selectedUri;
        status.setText("Extracting ZIP...");

        worker.execute(() -> {
            File folder = new File(
                    getExternalFilesDir(null),
                    "D-Compress-Extracted");

            folder.mkdirs();

            try (InputStream in =
                         getContentResolver().openInputStream(uri);
                 ZipInputStream zis = new ZipInputStream(in)) {

                ZipEntry entry;
                byte[] buffer = new byte[8192];
                int count;
                int extracted = 0;

                while ((entry = zis.getNextEntry()) != null) {
                    File out = new File(folder, entry.getName());

                    String root = folder.getCanonicalPath()
                            + File.separator;

                    if (!out.getCanonicalPath().startsWith(root)) {
                        throw new Exception("Unsafe ZIP entry blocked.");
                    }

                    if (entry.isDirectory()) {
                        out.mkdirs();
                    } else {
                        File parent = out.getParentFile();
                        if (parent != null) parent.mkdirs();

                        try (FileOutputStream fos =
                                     new FileOutputStream(out)) {
                            while ((count = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, count);
                            }
                        }
                        extracted++;
                    }

                    zis.closeEntry();
                }

                showStatus("ZIP extracted!\nFiles: " + extracted +
                        "\nFolder: " + folder.getAbsolutePath());
            } catch (Exception e) {
                showStatus("Extract error: " + e.getMessage());
            }
        });
    }

    private Uri saveMedia(byte[] bytes, String name,
                          String mime, String folder) throws Exception {

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);

        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, folder);
            values.put(MediaStore.MediaColumns.IS_P
