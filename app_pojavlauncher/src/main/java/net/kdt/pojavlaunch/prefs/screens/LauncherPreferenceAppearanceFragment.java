package net.kdt.pojavlaunch.prefs.screens;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import net.kdt.pojavlaunch.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class LauncherPreferenceAppearanceFragment extends LauncherPreferenceFragment {
    private static final String[] STRING_KEYS = {
            "novacraft_theme",
            "novacraft_button_style",
            "novacraft_background_texture",
            "novacraft_font_style",
            "novacraft_home_density"
    };
    private static final String[] BOOLEAN_KEYS = {
            "novacraft_animated_background",
            "novacraft_lightweight_mode"
    };

    private final ActivityResultLauncher<String> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),
                    this::exportTheme);

    private final ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    this::importTheme);

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_appearance);

        requirePreference("novacraft_export_theme").setOnPreferenceClickListener(preference -> {
            exportLauncher.launch(getString(R.string.novacraft_theme_file_name));
            return true;
        });

        requirePreference("novacraft_import_theme").setOnPreferenceClickListener(preference -> {
            importLauncher.launch(new String[]{"application/json", "text/plain"});
            return true;
        });

        requirePreference("novacraft_restore_theme").setOnPreferenceClickListener(preference -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.novacraft_appearance_reset_title)
                    .setMessage(R.string.novacraft_appearance_reset_summary)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        SharedPreferences.Editor editor = getPrefs().edit();
                        for (String key : STRING_KEYS) editor.remove(key);
                        for (String key : BOOLEAN_KEYS) editor.remove(key);
                        editor.apply();
                        Toast.makeText(requireContext(), R.string.novacraft_theme_reset, Toast.LENGTH_SHORT).show();
                    })
                    .show();
            return true;
        });
    }

    private SharedPreferences getPrefs() {
        return getPreferenceManager().getSharedPreferences();
    }

    private void exportTheme(Uri destination) {
        if (destination == null || getContext() == null) return;
        JSONObject json = new JSONObject();
        try {
            json.put("format", "novacraft-theme");
            json.put("version", 1);
            SharedPreferences prefs = getPrefs();
            for (String key : STRING_KEYS) {
                String fallback = "cyan";
                if ("novacraft_button_style".equals(key)) fallback = "rounded";
                else if ("novacraft_background_texture".equals(key)) fallback = "haze";
                else if ("novacraft_font_style".equals(key)) fallback = "system";
                else if ("novacraft_home_density".equals(key)) fallback = "comfortable";
                json.put(key, prefs.getString(key, fallback));
            }
            for (String key : BOOLEAN_KEYS) {
                json.put(key, prefs.getBoolean(key, "novacraft_animated_background".equals(key)));
            }
            byte[] bytes = json.toString(2).getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = requireContext().getContentResolver().openOutputStream(destination)) {
                if (output == null) throw new IOException("Could not open destination");
                output.write(bytes);
                output.flush();
            }
            Toast.makeText(requireContext(), R.string.novacraft_theme_exported, Toast.LENGTH_SHORT).show();
        } catch (JSONException | IOException | RuntimeException error) {
            Toast.makeText(requireContext(), R.string.novacraft_theme_io_error, Toast.LENGTH_LONG).show();
        }
    }

    private void importTheme(Uri source) {
        if (source == null || getContext() == null) return;
        try {
            JSONObject json;
            try (InputStream input = requireContext().getContentResolver().openInputStream(source)) {
                if (input == null) throw new IOException("Could not open theme");
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 65536) throw new IOException("Theme file is too large");
                    output.write(buffer, 0, count);
                }
                json = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }

            if (!"novacraft-theme".equals(json.optString("format"))
                    || json.optInt("version", -1) != 1) {
                throw new JSONException("Unsupported theme format");
            }

            SharedPreferences.Editor editor = getPrefs().edit();
            for (String key : STRING_KEYS) {
                if (!json.has(key)) continue;
                String value = json.optString(key, "");
                if (isAllowedValue(key, value)) editor.putString(key, value);
            }
            for (String key : BOOLEAN_KEYS) {
                if (json.has(key)) editor.putBoolean(key, json.optBoolean(key, false));
            }
            editor.apply();
            Toast.makeText(requireContext(), R.string.novacraft_theme_imported, Toast.LENGTH_LONG).show();
        } catch (IOException | JSONException | RuntimeException error) {
            Toast.makeText(requireContext(), R.string.novacraft_theme_invalid, Toast.LENGTH_LONG).show();
        }
    }

    private boolean isAllowedValue(String key, String value) {
        if ("novacraft_theme".equals(key)) {
            return "cyan".equals(value) || "violet".equals(value) || "amber".equals(value);
        }
        if ("novacraft_button_style".equals(key)) {
            return "rounded".equals(value) || "outline".equals(value) || "solid".equals(value);
        }
        if ("novacraft_background_texture".equals(key)) {
            return "haze".equals(value) || "grid".equals(value) || "plain".equals(value);
        }
        if ("novacraft_font_style".equals(key)) {
            return "system".equals(value) || "condensed".equals(value) || "mono".equals(value);
        }
        if ("novacraft_home_density".equals(key)) {
            return "compact".equals(value) || "comfortable".equals(value) || "spacious".equals(value);
        }
        return false;
    }
}
