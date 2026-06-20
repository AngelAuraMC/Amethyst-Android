package net.kdt.pojavlaunch.prefs.screens;

import android.os.Bundle;

import net.kdt.pojavlaunch.R;

/**
 * Experimental Settings — use with consideration, no support. The former
 * "force landscape" switch and the "Launcher appearance" category have moved
 * to their own top-level screen: see LauncherPreferenceAppearanceFragment /
 * pref_launcher_appearance.xml.
 */
public class LauncherPreferenceExperimentalFragment extends LauncherPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_experimental);
    }
}