package org.angelauramc.amethyst.game;

import static net.kdt.pojavlaunch.MainActivity.INTENT_MINECRAFT_VERSION;

import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.BaseActivity;
import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;

public class MinecraftActivity extends BaseActivity {
    private RelativeLayout mParentLayout; // Holds all surfaces we may have in the future

    private FrameLayout mMinecraftLayout; // Holds main game surface and controls
    private View mMinecraftView; // Holds game native surface, should be TextureView or SurfaceView
    private boolean gameStarted = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Init
        super.onCreate(savedInstanceState);
        mParentLayout = new RelativeLayout(this);
        setContentView(mParentLayout);

        mMinecraftLayout = new FrameLayout(this);
        mParentLayout.addView(mMinecraftLayout);

        // Setup native screen bridge
        mMinecraftView = createGameSurfaceThen(this::launchGame);
        mMinecraftLayout.addView(mMinecraftView);
    }

    private View createGameSurfaceThen(Runnable startGame) {
        // TODO: Add config argument and pick surface or texture
        SurfaceView surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                JREUtils.setupBridgeWindow(surfaceView.getHolder().getSurface());
                if(!gameStarted) {
                    new Thread(() -> {
                        startGame.run();
                    }, "JVM Main thread").start();
                    gameStarted = true;
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
    }

    public boolean launchGame() {
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
                Log.d("fuc", text);
            });


            Tools.LOCAL_RENDERER = "opengles_mobileglues"; // needed to be set to launch this way
            Tools.launchMinecraft(this, minecraftAccount, minecraftProfile, version, 8);
        }catch (Throwable e) {
            Tools.showErrorRemote(e);
        }

        return true;
    }
}
