package org.angelauramc.amethyst.game;

import android.os.Bundle;
import android.service.controls.Control;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import net.kdt.pojavlaunch.BaseActivity;
import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;

import java.io.IOException;

public abstract class GameActivity extends BaseActivity {
    /** Holds all views we may have in the future **/
    protected RelativeLayout mParentLayout;
    /** Holds main game surface and controls **/
    protected ControlLayout mGameLayout;
    /** Holds native game surface, should be TextureView or SurfaceView **/
    protected View mGameView;
    /** The native game surface **/
    protected Surface mGameSurface;
    /** If the game is running **/
    protected Thread mGameThread;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Init layout, screw XML, full programmatic layout!
        super.onCreate(savedInstanceState);
        mParentLayout = new RelativeLayout(this);
        setContentView(mParentLayout);

        // TODO: Create new ControlLayout
        mGameLayout = new ControlLayout(this);
        mParentLayout.addView(mGameLayout);
        try {
            mGameLayout.loadLayout(Tools.CTRLDEF_FILE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Logger.addLogListener(new Logger.eventLogListener() {
            @Override
            public void onEventLogged(String text) {
                Log.d("ingame", text);
            }
        });
    }
}
