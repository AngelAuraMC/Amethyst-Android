package net.kdt.pojavlaunch;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class PojavProfile {
	private static final String PROFILE_PREF = "pojav_profile";
	private static final String PROFILE_PREF_FILE = "file";

	public static SharedPreferences getPrefs(Context ctx) {
		return ctx.getSharedPreferences(PROFILE_PREF, Context.MODE_PRIVATE);
	}

    public static MinecraftAccount getCurrentProfileContent(@NonNull Context ctx, @Nullable String profileName) {
        return MinecraftAccount.load(profileName == null ? getCurrentProfileName(ctx) : profileName);
    }

    public static String getCurrentProfileName(Context ctx) {
        String name = getPrefs(ctx).getString(PROFILE_PREF_FILE, "");
        // A dirty fix
        if (!name.isEmpty() && name.startsWith(Tools.DIR_ACCOUNT_NEW) && name.endsWith(".json")) {
            name = name.substring(0, name.length() - 5).replace(Tools.DIR_ACCOUNT_NEW, "").replace(".json", "");
            setCurrentProfile(ctx, name);
        }
        return name;
    }

	public static List<MinecraftAccount> getAllProfiles(){
		List<MinecraftAccount> mcAccountList = new ArrayList<>();;
		for (String accountName : getAllProfilesList()){
			if (MinecraftAccount.load(accountName) != null) {
				mcAccountList.add(MinecraftAccount.load(accountName));
			}
		}
		return mcAccountList;
	}

	public static List<String> getAllProfilesList(){
		List<String> accountList = new ArrayList<>();
		File accountFolder = new File(Tools.DIR_ACCOUNT_NEW);
		if(accountFolder.exists() && accountFolder.list() != null){
			for (String fileName : Objects.requireNonNull(accountFolder.list())) {
				accountList.add(fileName.substring(0, fileName.length() - 5));
			}
		}
		return accountList;
	}
	
	public static void setCurrentProfile(@NonNull Context ctx, @Nullable  Object obj) {
		SharedPreferences.Editor pref = getPrefs(ctx).edit();
		
		try { if (obj instanceof String) {
                String acc = (String) obj;
				pref.putString(PROFILE_PREF_FILE, acc);
                //MinecraftAccount.clearTempAccount();
			} else if (obj == null) {
				pref.putString(PROFILE_PREF_FILE, "");
			} else {
				throw new IllegalArgumentException("Profile must be String.class or null");
			}
		} finally {
			pref.apply();
		}
	}
}
