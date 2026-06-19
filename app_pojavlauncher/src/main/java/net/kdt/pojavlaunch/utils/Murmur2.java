package net.kdt.pojavlaunch.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Murmur2 hashing as required by the CurseForge fingerprint API
 * (POST /v1/fingerprints). CurseForge computes this over the file bytes
 * with whitespace bytes (0x09, 0x0A, 0x0D, 0x20) stripped first — this is
 * their own normalization, not a property of murmur2 itself, and matching
 * it exactly is required for fingerprint matches to succeed.
 */
public class Murmur2 {

    private static final int SEED = 1;

    /** Computes the CurseForge-flavoured murmur2 fingerprint of a file as an unsigned long. */
    public static long hashFile(File file) throws IOException {
        byte[] raw = readAllBytes(file);
        byte[] filtered = stripWhitespace(raw);
        return hash32(filtered, SEED) & 0xFFFFFFFFL;
    }

    private static byte[] readAllBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0, read;
            while (offset < data.length && (read = fis.read(data, offset, data.length - offset)) != -1) {
                offset += read;
            }
            return data;
        }
    }

    /** Strips bytes 0x09 (tab), 0x0A (LF), 0x0D (CR), 0x20 (space) — CurseForge's normalization step. */
    private static byte[] stripWhitespace(byte[] input) {
        byte[] tmp = new byte[input.length];
        int n = 0;
        for (byte b : input) {
            if (b != 0x09 && b != 0x0A && b != 0x0D && b != 0x20) {
                tmp[n++] = b;
            }
        }
        byte[] result = new byte[n];
        System.arraycopy(tmp, 0, result, 0, n);
        return result;
    }

    /** Standard Murmur2 (32-bit) implementation. */
    private static int hash32(byte[] data, int seed) {
        final int m = 0x5bd1e995;
        final int r = 24;

        int length = data.length;
        int h = seed ^ length;
        int currentIndex = 0;

        while (length >= 4) {
            int k = (data[currentIndex] & 0xFF)
                    | ((data[currentIndex + 1] & 0xFF) << 8)
                    | ((data[currentIndex + 2] & 0xFF) << 16)
                    | ((data[currentIndex + 3] & 0xFF) << 24);

            k *= m;
            k ^= k >>> r;
            k *= m;

            h *= m;
            h ^= k;

            currentIndex += 4;
            length -= 4;
        }

        switch (length) {
            case 3:
                h ^= (data[currentIndex + 2] & 0xFF) << 16;
            case 2:
                h ^= (data[currentIndex + 1] & 0xFF) << 8;
            case 1:
                h ^= (data[currentIndex] & 0xFF);
                h *= m;
        }

        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;

        return h;
    }
}
