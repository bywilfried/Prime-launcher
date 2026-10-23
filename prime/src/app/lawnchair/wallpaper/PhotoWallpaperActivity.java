package app.lawnchair.wallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.InputStream;

/**
 * Bridges the Android image chooser to WallpaperManager without requiring storage permission.
 * The chosen image is applied only to the home screen; cancelling changes nothing.
 */
public class PhotoWallpaperActivity extends Activity {
    private static final int REQUEST_IMAGE = 1;
    private static final String TAG = "PhotoWallpaper";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) return;
        // Use the wallpaper action as the primary chooser target: it has multiple
        // handlers on Samsung, unlike PICK/GET_CONTENT which can launch directly.
        Intent wallpapers = new Intent(Intent.ACTION_SET_WALLPAPER);
        ArrayList<Intent> extras = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // Resolve each compatible gallery and file explorer explicitly, rather
        // than passing alternate implicit intents (which Samsung auto-launches).
        Intent gallery = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        gallery.setType("image/*");
        Intent files = new Intent(Intent.ACTION_GET_CONTENT);
        files.setType("image/*");
        files.addCategory(Intent.CATEGORY_OPENABLE);
        Intent documents = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        documents.setType("image/*");
        documents.addCategory(Intent.CATEGORY_OPENABLE);
        for (Intent query : new Intent[] { gallery, files, documents }) {
            List<ResolveInfo> handlers = getPackageManager().queryIntentActivities(query, 0);
            for (ResolveInfo handler : handlers) {
                if (handler.activityInfo == null) continue;
                String key = handler.activityInfo.packageName + "/" + handler.activityInfo.name;
                if (!seen.add(key)) continue;
                Intent explicit = new Intent(query);
                explicit.setClassName(handler.activityInfo.packageName,
                        handler.activityInfo.name);
                extras.add(explicit);
            }
        }
        Intent chooser = Intent.createChooser(wallpapers, "Fonds d'écran");
        if (!extras.isEmpty()) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extras.toArray(new Intent[0]));
        }
        try {
            startActivityForResult(chooser, REQUEST_IMAGE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Aucune application de fond d'écran disponible",
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMAGE) return;
        Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
        if (uri == null) {
            finish();
            return;
        }
        new Thread(() -> {
            boolean success = false;
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                if (stream != null) {
                    WallpaperManager.getInstance(this).setStream(stream, null, true,
                            WallpaperManager.FLAG_SYSTEM);
                    success = true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Unable to apply selected wallpaper", e);
            }
            final boolean applied = success;
            runOnUiThread(() -> {
                Toast.makeText(this, applied ? "Fond d'écran appliqué"
                        : "Impossible d'appliquer cette image", Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }
}
