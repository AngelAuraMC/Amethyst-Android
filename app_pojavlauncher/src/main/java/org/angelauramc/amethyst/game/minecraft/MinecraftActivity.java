package org.angelauramc.amethyst.game.minecraft;

import static net.kdt.pojavlaunch.MainActivity.INTENT_MINECRAFT_VERSION;
import static net.kdt.pojavlaunch.Tools.LOCAL_RENDERER;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_USE_ALTERNATE_SURFACE;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.angelauramc.amethyst.game.GameActivity;

import java.io.File;
import java.io.IOException;

public class MinecraftActivity extends GameActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Sets up everything not Minecraft related
        super.onCreate(savedInstanceState);
        // Setup native screen bridge
        mGameView = createGameSurfaceThen(this::launchGame);
        mGameLayout.addView(mGameView);
    }

    protected View createGameSurfaceThen(Runnable startGame) {
        // FIXME: Fix ASR issues on Kopper and MojVK with Vulkan layer, see https://github.com/AngelAuraMC/Amethyst-Android/issues/249
        if(PREF_USE_ALTERNATE_SURFACE && !LOCAL_RENDERER.equals("opengles3_desktopgl_zink_kopper")) {
            SurfaceView surfaceView = new SurfaceView(this);
            surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(@NonNull SurfaceHolder holder) {
                    mGameSurface = surfaceView.getHolder().getSurface();
                    JREUtils.setupBridgeWindow(mGameSurface);
                    if (mGameThread == null || !mGameThread.isAlive()) {
                        mGameThread = new Thread(() -> {
                            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
                            startGame.run();
                        }, "JVM Main thread");
                        mGameThread.start();
                    }
                }

                @Override
                public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
//                refreshSize();
                }

                @Override
                public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                    /*
                    Surface recreation in SurfaceView happens very often. When tabbing back in from
                    out, when minimizing floating window, when turning into floating window, etc.
                    Whenever the surface isn't in view, it is destroyed. When going into floating
                    window, it appears to automatically release the associated ANativeWindow. This
                    can cause a crash if not handled.
                     */
                }
            });
            return surfaceView;
        } else {
            TextureView textureView = new TextureView(this);
            textureView.setOpaque(true);
            textureView.setAlpha(1.0f);

            textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {

                @Override
                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                    mGameSurface = new Surface(surface);
                    JREUtils.setupBridgeWindow(mGameSurface);
                    if (mGameThread == null || !mGameThread.isAlive()) {
                        mGameThread = new Thread(() -> {
                            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
                            startGame.run();
                        }, "JVM Main thread");
                        mGameThread.start();
                    }
                }

                @Override
                public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
//                    refreshSize();
                }

                @Override
                public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                    /*
                    Surface recreation in TextureView can only really happen once, when turning
                    into a floating window. Subsequent turns to floating window no longer trigger
                    recreation. Tabbing out and in does not trigger recreation.
                     */
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
                    // TODO: Triggers on eglSwapBuffers. Add a loading message and make it end here
                }
            });
            return textureView;
        }
    }

    public void launchGame() {
        MinecraftProfile minecraftProfile = LauncherProfiles.getCurrentProfile();
        MinecraftAccount minecraftAccount = PojavProfile.getCurrentProfileContent(this, null);

        String version = getIntent().getStringExtra(INTENT_MINECRAFT_VERSION);
        version = version == null ? minecraftProfile.lastVersionId : version;

        try {
            File latestLogFile = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
            if (!latestLogFile.exists() && !latestLogFile.createNewFile())
                throw new IOException("Failed to create a new log file");
            Logger.begin(latestLogFile.getAbsolutePath());
            Logger.addLogListener(text -> {
                Log.d("GameLog", text);
            });

            Tools.LOCAL_RENDERER = "opengles_mobileglues"; // needed to be set to launch this way
            Tools.launchMinecraft(this, minecraftAccount, minecraftProfile, version, 8);
        } catch (Throwable e) {
            Tools.showErrorRemote(e);

        }
    }
}
