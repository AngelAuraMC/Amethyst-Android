package net.kdt.pojavlaunch.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Keeps the saved server list (servers.dat) shared between every version/instance.
 *
 * The "master" copy lives in the default game directory (.minecraft). Profiles that use
 * their own game directory (custom instances, modpacks...) get the most recent copy when
 * the game starts, and any change made while playing is written back to the master copy,
 * so a server saved in one version shows up in all the others.
 * Strategy: the most recently modified file wins.
 */
public final class ServerListSync {
    private static final String TAG = "ServerListSync";
    private static final String FILE_NAME = "servers.dat";
    private static final long POLL_INTERVAL_MS = 3000;
    // FAT/exFAT only store modification times with 2 second precision
    private static final long TIME_TOLERANCE_MS = 2500;

    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static File sInstanceDir;
    private static long sLastSeenModified = -1;

    private static final Runnable sPoll = new Runnable() {
        @Override
        public void run() {
            flush();
            if (sInstanceDir != null) sHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private ServerListSync() {}

    private static File masterFile() {
        return new File(Tools.DIR_GAME_NEW, FILE_NAME);
    }

    private static boolean isMasterDir(File gameDir) {
        try {
            return new File(Tools.DIR_GAME_NEW).getCanonicalFile().equals(gameDir.getCanonicalFile());
        } catch (IOException e) {
            return gameDir.getAbsolutePath().equals(new File(Tools.DIR_GAME_NEW).getAbsolutePath());
        }
    }

    /** Call right before starting the game: both sides end up with the newest list. */
    public static void syncBeforeLaunch(File gameDir) {
        try {
            if (gameDir == null || isMasterDir(gameDir)) return;
            File master = masterFile();
            File instance = new File(gameDir, FILE_NAME);
            if (!master.exists() && !instance.exists()) return;

            if (!instance.exists()) {
                copy(master, instance);
            } else if (!master.exists()) {
                copy(instance, master);
            } else if (master.lastModified() - instance.lastModified() > TIME_TOLERANCE_MS) {
                copy(master, instance);
            } else if (instance.lastModified() - master.lastModified() > TIME_TOLERANCE_MS) {
                copy(instance, master);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not sync the server list before launch", e);
        }
    }

    /** Watches the instance's list while the game runs and mirrors changes to the master copy. */
    public static void startWatching(File gameDir) {
        if (gameDir == null || isMasterDir(gameDir)) return;
        stopWatching();
        sInstanceDir = gameDir;
        sLastSeenModified = new File(gameDir, FILE_NAME).lastModified();
        sHandler.postDelayed(sPoll, POLL_INTERVAL_MS);
    }

    public static void stopWatching() {
        flush();
        sHandler.removeCallbacks(sPoll);
        sInstanceDir = null;
    }

    /** Copies the instance list to the master copy if it changed. Safe to call any time. */
    public static void flush() {
        File dir = sInstanceDir;
        if (dir == null) return;
        try {
            File instance = new File(dir, FILE_NAME);
            if (!instance.exists()) return;
            long modified = instance.lastModified();
            if (modified == sLastSeenModified) return;
            sLastSeenModified = modified;
            copy(instance, masterFile());
        } catch (Exception e) {
            Log.w(TAG, "Could not mirror the server list", e);
        }
    }

    private static void copy(File from, File to) throws IOException {
        File parent = to.getParentFile();
        if (parent != null && !parent.exists()) //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        File tmp = new File(to.getPath() + ".tmp");
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        if (to.exists() && !to.delete()) throw new IOException("Could not replace " + to);
        if (!tmp.renameTo(to)) throw new IOException("Could not move " + tmp + " to " + to);
        //noinspection ResultOfMethodCallIgnored
        to.setLastModified(from.lastModified());
    }
}
