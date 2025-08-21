package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.os.Vibrator;

import top.fifthlight.touchcontroller.proxy.client.LauncherProxyClient;
import top.fifthlight.touchcontroller.proxy.client.MessageTransport;
import top.fifthlight.touchcontroller.proxy.client.android.transport.UnixSocketTransportKt;
import top.fifthlight.touchcontroller.proxy.message.VibrateMessage;

import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

public class TouchControllerUtils {
    private TouchControllerUtils() {
    }

    public static LauncherProxyClient proxyClient;
    private static final String socketName = "Amethyst";

    private static class VibrationHandler implements LauncherProxyClient.VibrationHandler {
        private final Vibrator vibrator;

        public VibrationHandler(Vibrator vibrator) {
            this.vibrator = vibrator;
        }

        @Override
        @SuppressWarnings("DEPRECATION")
        public void vibrate(@NonNull VibrateMessage.Kind kind) {
            vibrator.vibrate(LauncherPreferences.PREF_TOUCHCONTROLLER_VIBRATE_LENGTH);
        }
    }

    private static final SparseIntArray pointerIdMap = new SparseIntArray();
    private static int nextPointerId = 1;

    public static void processTouchEvent(MotionEvent motionEvent, View view) {
        if (proxyClient == null) {
            return;
        }
        int pointerId;
        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pointerId = nextPointerId++;
                pointerIdMap.put(motionEvent.getPointerId(0), pointerId);
                proxyClient.addPointer(pointerId, motionEvent.getX(0) / view.getWidth(), motionEvent.getY(0) / view.getHeight());
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                pointerId = nextPointerId++;
                int actionIndex = motionEvent.getActionIndex();
                pointerIdMap.put(motionEvent.getPointerId(actionIndex), pointerId);
                proxyClient.addPointer(pointerId, motionEvent.getX(actionIndex) / view.getWidth(), motionEvent.getY(actionIndex) / view.getHeight());
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < motionEvent.getPointerCount(); i++) {
                    pointerId = pointerIdMap.get(motionEvent.getPointerId(i));
                    if (pointerId == 0) {
                        Log.d("TouchController", "Move pointerId is 0");
                        continue;
                    }
                    proxyClient.addPointer(pointerId, motionEvent.getX(i) / view.getWidth(), motionEvent.getY(i) / view.getHeight());
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (proxyClient != null) {
                    proxyClient.clearPointer();
                    pointerIdMap.clear();
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (proxyClient != null) {
                    int i = motionEvent.getActionIndex();
                    pointerId = pointerIdMap.get(motionEvent.getPointerId(i));
                    if (pointerId == 0) {
                        Log.d("TouchController", "Pointer up pointerId is 0");
                        break;
                    }
                    pointerIdMap.delete(pointerId);
                    proxyClient.removePointer(pointerId);
                }
                break;
        }
    }

    public static void initialize(Context context) {
        if (proxyClient != null) {
            return;
        }
        try {
            Os.setenv("TOUCH_CONTROLLER_PROXY_SOCKET", socketName, true);
        } catch (ErrnoException e) {
            Log.w("TouchController", "Failed to set TouchController environment variable", e);
        }
        MessageTransport transport = UnixSocketTransportKt.UnixSocketTransport(socketName);
        proxyClient = new LauncherProxyClient(transport);
        proxyClient.run();
        Vibrator vibrator = ContextCompat.getSystemService(context, Vibrator.class);
        if (vibrator != null) {
            LauncherProxyClient.VibrationHandler vibrationHandler = new VibrationHandler(vibrator);
            proxyClient.setVibrationHandler(vibrationHandler);
        }
    }
}
