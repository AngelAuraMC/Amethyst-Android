package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stores a user's CurseForge API key encrypted with an AES key held by Android Keystore.
 * The API key is never written to source files, logs or plain-text preferences.
 */
public final class NovaCraftApiKeyStore {
    private static final String KEY_ALIAS = "NovaCraft.CurseForge.ApiKey.v1";
    private static final String PREFS_NAME = "novacraft_encrypted_keys";
    private static final String PREF_CURSEFORGE = "curseforge_api_key";
    private static final int GCM_TAG_BITS = 128;

    private NovaCraftApiKeyStore() {}

    public static String getCurseForgeKey(Context context) throws Exception {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String encoded = preferences.getString(PREF_CURSEFORGE, null);
        if (encoded == null || encoded.isEmpty()) return null;

        byte[] packed = Base64.decode(encoded, Base64.NO_WRAP);
        if (packed.length < 13) throw new IllegalStateException("Encrypted key data is incomplete");
        ByteBuffer buffer = ByteBuffer.wrap(packed);
        int ivLength = buffer.getInt();
        if (ivLength < 12 || ivLength > 16 || buffer.remaining() <= ivLength) {
            throw new IllegalStateException("Encrypted key data is invalid");
        }
        byte[] iv = new byte[ivLength];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plain = cipher.doFinal(ciphertext);
        return new String(plain, StandardCharsets.UTF_8);
    }

    public static void saveCurseForgeKey(Context context, String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

        ByteBuffer packed = ByteBuffer.allocate(4 + iv.length + ciphertext.length);
        packed.putInt(iv.length);
        packed.put(iv);
        packed.put(ciphertext);
        String encoded = Base64.encodeToString(packed.array(), Base64.NO_WRAP);

        context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_CURSEFORGE, encoded)
                .apply();
    }

    public static void removeCurseForgeKey(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_CURSEFORGE)
                .apply();
    }

    public static boolean hasSavedCurseForgeKey(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .contains(PREF_CURSEFORGE);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
            throw new IllegalStateException("Android Keystore key entry has an unexpected type");
        }

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }
}
