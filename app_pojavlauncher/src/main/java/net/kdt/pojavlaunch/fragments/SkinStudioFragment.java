package net.kdt.pojavlaunch.fragments;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.views.SkinPreviewView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

public class SkinStudioFragment extends Fragment {
    public static final String TAG = "NovaCraftSkinStudio";
    private static final long MAX_SKIN_BYTES = 8L * 1024L * 1024L;
    private static final String PREF_ACTIVE_SKIN = "novacraft_active_skin";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<String> exportLauncher;
    private SkinPreviewView avatarPreview;
    private ImageView texturePreview;
    private TextView activeText;
    private TextView emptyText;
    private LinearLayout skinList;
    private File pendingExport;

    public SkinStudioFragment() {
        super(R.layout.fragment_skin_studio);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onSkinImport);
        exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument(), this::onSkinExport);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        avatarPreview = view.findViewById(R.id.skin_studio_avatar);
        texturePreview = view.findViewById(R.id.skin_studio_texture);
        activeText = view.findViewById(R.id.skin_studio_active);
        emptyText = view.findViewById(R.id.skin_studio_empty);
        skinList = view.findViewById(R.id.skin_studio_list);

        Button upload = view.findViewById(R.id.skin_studio_upload);
        Button refresh = view.findViewById(R.id.skin_studio_refresh);
        upload.setOnClickListener(v -> importLauncher.launch(new String[]{"image/png"}));
        refresh.setOnClickListener(v -> refreshSkins());
        refreshSkins();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (skinList != null) refreshSkins();
    }

    private File getSkinDirectory() {
        File folder = new File(requireContext().getFilesDir(), "novacraft/skins");
        if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Unable to create private skin library.");
        return folder;
    }

    private void onSkinImport(@Nullable Uri uri) {
        if (uri == null || getContext() == null) return;
        final android.content.ContentResolver resolver = requireContext().getContentResolver();
        final File directory = getSkinDirectory();
        final String tooLargeMessage = getString(R.string.novacraft_skin_too_large);
        final String invalidSkinMessage = getString(R.string.novacraft_skin_invalid);
        new Thread(() -> {
            File temporary = null;
            try {
                String originalName = displayName(uri);
                String safeName = safeSkinName(originalName);
                temporary = new File(directory, safeName + ".importing");
                long total = 0;
                try (InputStream input = resolver.openInputStream(uri);
                     OutputStream output = new FileOutputStream(temporary)) {
                    if (input == null) throw new java.io.IOException("Unable to read selected PNG.");
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_SKIN_BYTES) throw new java.io.IOException(tooLargeMessage);
                        output.write(buffer, 0, read);
                    }
                }
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(temporary.getAbsolutePath(), bounds);
                boolean validDimensions = bounds.outWidth == 64 && (bounds.outHeight == 64 || bounds.outHeight == 32);
                Bitmap decoded = validDimensions ? BitmapFactory.decodeFile(temporary.getAbsolutePath()) : null;
                if (!validDimensions || decoded == null) {
                    if (decoded != null) decoded.recycle();
                    throw new java.io.IOException(invalidSkinMessage);
                }
                decoded.recycle();

                File destination = uniqueSkinFile(new File(directory, safeName + ".png"));
                if (!temporary.renameTo(destination)) throw new java.io.IOException("Unable to save skin PNG.");
                String savedName = destination.getName();
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    requireContext().getSharedPreferences("novacraft_skin_studio", 0)
                            .edit().putString(PREF_ACTIVE_SKIN, savedName).apply();
                    Toast.makeText(requireContext(), R.string.novacraft_skin_imported, Toast.LENGTH_LONG).show();
                    refreshSkins();
                });
            } catch (Exception error) {
                if (temporary != null) temporary.delete();
                String message = error.getMessage() == null ? error.toString() : error.getMessage();
                mainHandler.post(() -> {
                    if (isAdded()) Toast.makeText(requireContext(),
                            getString(R.string.novacraft_skin_import_failed, message), Toast.LENGTH_LONG).show();
                });
            }
        }, "NovaCraft-Skin-Import").start();
    }

    private void onSkinExport(@Nullable Uri uri) {
        if (uri == null || pendingExport == null || getContext() == null) return;
        File source = pendingExport;
        pendingExport = null;
        final android.content.ContentResolver resolver = requireContext().getContentResolver();
        new Thread(() -> {
            try (InputStream input = new java.io.BufferedInputStream(new java.io.FileInputStream(source));
                 OutputStream output = resolver.openOutputStream(uri, "wt")) {
                if (output == null) throw new java.io.IOException("Unable to open export destination.");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                mainHandler.post(() -> {
                    if (isAdded()) Toast.makeText(requireContext(), R.string.novacraft_skin_export, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                String message = error.getMessage() == null ? error.toString() : error.getMessage();
                mainHandler.post(() -> {
                    if (isAdded()) Toast.makeText(requireContext(),
                            getString(R.string.novacraft_skin_export_failed, message), Toast.LENGTH_LONG).show();
                });
            }
        }, "NovaCraft-Skin-Export").start();
    }

    private void refreshSkins() {
        if (!isAdded() || skinList == null) return;
        skinList.removeAllViews();
        File[] skins;
        try {
            skins = getSkinDirectory().listFiles((dir, name) -> {
                if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) return false;
                File candidate = new File(dir, name);
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(candidate.getAbsolutePath(), bounds);
                return bounds.outWidth == 64 && (bounds.outHeight == 64 || bounds.outHeight == 32);
            });
        } catch (Exception error) {
            skins = null;
        }
        if (skins == null || skins.length == 0) {
            emptyText.setVisibility(View.VISIBLE);
            activeText.setText(R.string.novacraft_skin_empty);
            avatarPreview.setSkin(null);
            texturePreview.setImageDrawable(null);
            return;
        }
        Arrays.sort(skins, Comparator.comparingLong(File::lastModified).reversed());
        emptyText.setVisibility(View.GONE);
        String selected = requireContext().getSharedPreferences("novacraft_skin_studio", 0)
                .getString(PREF_ACTIVE_SKIN, skins[0].getName());
        File active = findByName(skins, selected);
        if (active == null) active = skins[0];
        showPreview(active);
        for (File skin : skins) addSkinCard(skin, skin.equals(active));
    }

    private void addSkinCard(File skin, boolean active) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        int padding = dp(9);
        card.setPadding(padding, padding, padding, padding);
        card.setBackgroundResource(R.drawable.background_overlay);

        SkinPreviewView preview = new SkinPreviewView(requireContext());
        preview.setLayoutParams(new LinearLayout.LayoutParams(dp(54), dp(78)));
        Bitmap bitmap = BitmapFactory.decodeFile(skin.getAbsolutePath());
        if (bitmap != null) preview.setSkin(bitmap);
        card.addView(preview);

        LinearLayout details = new LinearLayout(requireContext());
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(9), 0, 0, 0);
        TextView name = new TextView(requireContext());
        name.setText(skin.getName());
        name.setTextColor(getResources().getColor(R.color.primary_text));
        name.setTextSize(12);
        name.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        details.addView(name);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(skin.getAbsolutePath(), options);
        TextView dimensions = new TextView(requireContext());
        dimensions.setText(getString(R.string.novacraft_skin_dimensions, options.outWidth, options.outHeight));
        dimensions.setTextColor(getResources().getColor(R.color.secondary_text));
        dimensions.setTextSize(10);
        details.addView(dimensions);

        TextView selectedText = new TextView(requireContext());
        if (active) selectedText.setText(R.string.novacraft_skin_selected);
        selectedText.setTextColor(getResources().getColor(R.color.novacraft_accent));
        selectedText.setTextSize(10);
        details.addView(selectedText);

        Button viewButton = new Button(requireContext());
        viewButton.setText(R.string.novacraft_skin_view);
        viewButton.setOnClickListener(v -> {
            requireContext().getSharedPreferences("novacraft_skin_studio", 0)
                    .edit().putString(PREF_ACTIVE_SKIN, skin.getName()).apply();
            refreshSkins();
        });
        details.addView(viewButton);

        Button exportButton = new Button(requireContext());
        exportButton.setText(R.string.novacraft_skin_export);
        exportButton.setOnClickListener(v -> {
            pendingExport = skin;
            exportLauncher.launch(skin.getName());
        });
        details.addView(exportButton);

        Button deleteButton = new Button(requireContext());
        deleteButton.setText(R.string.novacraft_skin_delete);
        deleteButton.setOnClickListener(v -> confirmDelete(skin));
        details.addView(deleteButton);

        card.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(8);
        skinList.addView(card, cardParams);
    }

    private void showPreview(File skin) {
        Bitmap bitmap = BitmapFactory.decodeFile(skin.getAbsolutePath());
        if (bitmap == null) return;
        avatarPreview.setSkin(bitmap);
        texturePreview.setImageBitmap(bitmap);
        activeText.setText(getString(R.string.novacraft_skin_active, skin.getName()));
    }

    private void confirmDelete(File skin) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.novacraft_skin_delete_title)
                .setMessage(skin.getName())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.novacraft_skin_delete, (dialog, which) -> {
                    if (skin.delete()) {
                        String active = requireContext().getSharedPreferences("novacraft_skin_studio", 0)
                                .getString(PREF_ACTIVE_SKIN, "");
                        if (skin.getName().equals(active)) {
                            requireContext().getSharedPreferences("novacraft_skin_studio", 0)
                                    .edit().remove(PREF_ACTIVE_SKIN).apply();
                        }
                        Toast.makeText(requireContext(), R.string.novacraft_skin_deleted, Toast.LENGTH_SHORT).show();
                        refreshSkins();
                    }
                }).show();
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = requireContext().getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String safeSkinName(String original) {
        String name = original == null ? "skin" : original.replaceAll("(?i)\\.png$", "");
        name = name.replaceAll("[^A-Za-z0-9._-]", "_").replace("..", "_");
        return name.isEmpty() ? "skin" : name;
    }

    private static File uniqueSkinFile(File file) {
        if (!file.exists()) return file;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : ".png";
        int index = 2;
        File candidate;
        do {
            candidate = new File(file.getParentFile(), base + "-" + index + ext);
            index++;
        } while (candidate.exists());
        return candidate;
    }

    private static File findByName(File[] files, String name) {
        if (files == null || name == null) return null;
        for (File file : files) if (name.equals(file.getName())) return file;
        return null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
