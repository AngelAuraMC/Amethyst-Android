package net.kdt.pojavlaunch.prefs;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import net.kdt.pojavlaunch.EfficientAndroidLWJGLKeycode;
import net.kdt.pojavlaunch.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Name-based picker for the keyboard panning chat key lists, similar to the
 * key mapping spinner of the control editor. The stored value stays a
 * comma-separated string of GLFW key codes, parsed by LauncherPreferences.
 */
public class KeyboardPanKeysPreference extends Preference {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private final String mDefaultValue;

    public KeyboardPanKeysPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mDefaultValue = attrs.getAttributeValue(ANDROID_NS, "defaultValue");
        setOnPreferenceClickListener(preference -> {
            showKeyPickerDialog();
            return true;
        });
    }

    @Override
    public void onAttachedToHierarchy(@NonNull androidx.preference.PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);
        refreshSummary();
    }

    private void showKeyPickerDialog() {
        Context ctx = getContext();
        List<Integer> keys = parseKeyCodes(getPersistedString(mDefaultValue));

        View dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_keyboard_pan_keys, null);
        LinearLayout keyList = dialogView.findViewById(R.id.chatPanKeys_list);
        Spinner spinner = dialogView.findViewById(R.id.chatPanKeys_spinner);
        Button addButton = dialogView.findViewById(R.id.chatPanKeys_addButton);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx, R.layout.item_centered_textview,
                EfficientAndroidLWJGLKeycode.generateKeyName());
        spinner.setAdapter(adapter);

        rebuildKeyList(keyList, keys);

        addButton.setOnClickListener(v -> {
            int keycode = EfficientAndroidLWJGLKeycode.getValueByIndex(spinner.getSelectedItemPosition());
            if (!keys.contains(keycode)) {
                keys.add(keycode);
                rebuildKeyList(keyList, keys);
            }
        });

        new AlertDialog.Builder(ctx)
                .setTitle(getTitle())
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    persistString(joinKeyCodes(keys));
                    refreshSummary();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Re-creates one tappable row per key; tapping a key removes it */
    private void rebuildKeyList(LinearLayout container, List<Integer> keys) {
        container.removeAllViews();
        int padding = (int) (12 * getResources().getDisplayMetrics().density);
        for (Integer key : keys) {
            TextView keyView = new TextView(container.getContext());
            keyView.setText(getKeyName(key));
            keyView.setGravity(Gravity.CENTER);
            keyView.setPadding(0, padding, 0, padding);
            keyView.setOnClickListener(v -> {
                keys.remove(key);
                rebuildKeyList(container, keys);
            });
            container.addView(keyView);
        }
    }

    private void refreshSummary() {
        List<Integer> keys = parseKeyCodes(getPersistedString(mDefaultValue));
        if (keys.isEmpty()) {
            setSummary(R.string.mcl_setting_chat_pan_keys_none);
            return;
        }
        StringBuilder nameList = new StringBuilder();
        for (int key : keys) {
            if (nameList.length() > 0) nameList.append(", ");
            nameList.append(getKeyName(key));
        }
        setSummary(nameList.toString());
    }

    private static String getKeyName(int keycode) {
        return EfficientAndroidLWJGLKeycode.generateKeyName()[EfficientAndroidLWJGLKeycode.getIndexByValue(keycode)];
    }

    private static List<Integer> parseKeyCodes(String stored) {
        List<Integer> keys = new ArrayList<>();
        if (stored != null) {
            for (String part : stored.split(",")) {
                try {
                    keys.add(Integer.valueOf(part.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return keys;
    }

    private static String joinKeyCodes(List<Integer> keys) {
        StringBuilder joined = new StringBuilder();
        for (int key : keys) {
            if (joined.length() > 0) joined.append(',');
            joined.append(key);
        }
        return joined.toString();
    }
}
