package net.kdt.pojavlaunch.customcontrols.mouse;

import static net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_MOUSE_GRAB_FORCE;

import android.content.SharedPreferences;
import android.os.Build;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import net.kdt.pojavlaunch.GrabListener;
import net.kdt.pojavlaunch.MinecraftGLSurface;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import org.lwjgl.glfw.CallbackBridge;

import java.util.function.Consumer;

@RequiresApi(api = Build.VERSION_CODES.O)
public class AndroidPointerCapture implements ViewTreeObserver.OnWindowFocusChangeListener, View.OnCapturedPointerListener, GrabListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private static final float TOUCHPAD_SCROLL_THRESHOLD = 1;
    private final AbstractTouchpad mTouchpad;
    private final View mHostView;
    private final float mMousePrescale = Tools.dpToPx(1);
    private final PointerTracker mPointerTracker = new PointerTracker();
    private final Scroller mScroller = new Scroller(TOUCHPAD_SCROLL_THRESHOLD);
    private final float[] mVector = mPointerTracker.getMotionVector();

    private int mInputDeviceIdentifier;
    private boolean mDeviceSupportsRelativeAxis;

    public AndroidPointerCapture(AbstractTouchpad touchpad, View hostView) {
        this.mTouchpad = touchpad;
        this.mHostView = hostView;
        hostView.setOnCapturedPointerListener(this);
        hostView.getViewTreeObserver().addOnWindowFocusChangeListener(this);
        DEFAULT_PREF.registerOnSharedPreferenceChangeListener(this);
        CallbackBridge.addGrabListener(this);
    }

    /**
     * Checks whether or not the touchpad is already enabled and if user prefers virtual cursor
     * if they don't, the touchpad is not enabled
     */
    private void enableTouchpadIfNecessary() {
        if(!mTouchpad.getDisplayState() && PREF_MOUSE_GRAB_FORCE) mTouchpad.enable(true);
    }

    // Needed so it releases the cursor when inside game menu
    @Override
    public void onGrabState(boolean isGrabbing) {
        handleAutomaticCapture();
    }
    // It's only here so the side-dialog changes it live
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        if (sharedPreferences.getBoolean("always_grab_mouse", true)){
            enableTouchpadIfNecessary();
        } else mTouchpad.disable();
        handleAutomaticCapture();
    }

    public void handleAutomaticCapture() {
        // isGrabbing checks for whether we are in menu
        if (!CallbackBridge.isGrabbing()
        && !PREF_MOUSE_GRAB_FORCE) {
            mHostView.releasePointerCapture();
            return;
        }
        if (mHostView.hasPointerCapture()) {
            enableTouchpadIfNecessary();
        }
        if (!mHostView.hasWindowFocus()) {
            mHostView.requestFocus();
        } else {
            mHostView.requestPointerCapture();
        }
    }

    @Override
    public boolean onCapturedPointer(View view, MotionEvent event) {
        checkSameDevice(event.getDevice());
        int axisX, axisY;
        // Sources can claim to be a relative device by belonging to the trackball class, if so then
        // we could just use the relative axis directly but some OEMs report absolute touchpads as
        // trackballs, verify that it gives us relative input with mDeviceSupportsRelativeAxis and
        // hope whatever relative value it spits out is valid, otherwise, revert to non-relative and
        // hope the OS does magic
        if (mDeviceSupportsRelativeAxis) {
            axisX = MotionEvent.AXIS_RELATIVE_X;
            axisY = MotionEvent.AXIS_RELATIVE_Y;
        } else {
            axisX = MotionEvent.AXIS_X;
            axisY = MotionEvent.AXIS_Y;
        }

        // Yes, we actually not only receive relative mouse events here, but also absolute touchpad ones!
        // Therefore, we need to know when it's a touchpad and when it's a mouse.
        if ((event.getSource() & InputDevice.SOURCE_CLASS_TRACKBALL) != 0){
            processBatchEvents(event, axisX, axisY, this::processMousePos);
            mVector[0] = event.getAxisValue(axisX);
            mVector[1] = event.getAxisValue(axisY);
            return processAndSendMotionEvent(event);
        }
        // If it's not a trackball, it's likely a touchpad and needs tracking like a touchscreen.
        processBatchEvents(event, MotionEvent.AXIS_X, MotionEvent.AXIS_Y, mPointerTracker::trackEvent);
        // Touchscreens should never be using relative axis...right? Well it's an easy fix if there's
        // a bug report!
        mPointerTracker.trackEvent(event); // This also updates the mVector variable.
        return processAndSendMotionEvent(event);
    }

    /**
     * Android handles high refresh rate mice by batching their movements in between screen refresh.
     * This basically locks your input hz to what your screen hz is.
     * Screw that, handle it as the high hz input that it is, to the extent permitted by the rules.
     * Processes historical batched events one by one, sets mVector, and runs {@code moveCursorWithEvent}.
     * @param event The MotionEvent
     * @param axisX The axis identifier for the axis value to retrieve
     * @param axisY The axis identifier for the axis value to retrieve
     * @param moveCursorWithEvent Lambda that triggers cursor movement. Runs after mVector update.
     */
    private void processBatchEvents(MotionEvent event, int axisX, int axisY, Consumer<MotionEvent> moveCursorWithEvent){
        // Process batched events first as said in Android docs https://developer.android.com/reference/android/view/MotionEvent#batching
        for (int h = 0; h < event.getHistorySize(); h++){
            mVector[0] = event.getHistoricalAxisValue(axisX, h);
            mVector[1] = event.getHistoricalAxisValue(axisY, h);
            // All historical values are ACTION_MOVE. This is the lamest part. Damn your rules.
            moveCursorWithEvent.accept(event);
        }
    }

    private void processMousePos(MotionEvent event) {
        if(!CallbackBridge.isGrabbing()) {
            enableTouchpadIfNecessary();
            // Yes, if the user's touchpad is multi-touch we will also receive events for that.
            // So, handle the scrolling gesture ourselves.
            mVector[0] *= mMousePrescale;
            mVector[1] *= mMousePrescale;
            if(event.getPointerCount() < 2) {
                mTouchpad.applyMotionVector(mVector);
                mScroller.resetScrollOvershoot();
            } else {
                mScroller.performScroll(mVector);
            }
        } else {
            // Position is updated by many events, hence it is send regardless of the event value
            CallbackBridge.mouseX += (mVector[0] * LauncherPreferences.PREF_SCALE_FACTOR);
            CallbackBridge.mouseY += (mVector[1] * LauncherPreferences.PREF_SCALE_FACTOR);
            CallbackBridge.sendCursorPos(CallbackBridge.mouseX, CallbackBridge.mouseY);
        }
    }

    private boolean processAndSendMotionEvent(MotionEvent event) {
        processMousePos(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                return true;
            case MotionEvent.ACTION_BUTTON_PRESS:
                return MinecraftGLSurface.sendMouseButtonUnconverted(event.getActionButton(), true);
            case MotionEvent.ACTION_BUTTON_RELEASE:
                return MinecraftGLSurface.sendMouseButtonUnconverted(event.getActionButton(), false);
            case MotionEvent.ACTION_SCROLL:
                CallbackBridge.sendScroll(
                        event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                        event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                );
                return true;
            case MotionEvent.ACTION_UP:
                mPointerTracker.cancelTracking();
                return true;
            default:
                return false;
        }
    }

    private void checkSameDevice(InputDevice inputDevice) {
        int newIdentifier = inputDevice.getId();
        if(mInputDeviceIdentifier != newIdentifier) {
            reinitializeDeviceSpecificProperties(inputDevice);
            mInputDeviceIdentifier = newIdentifier;
        }
    }

    private void reinitializeDeviceSpecificProperties(InputDevice inputDevice) {
        mPointerTracker.cancelTracking();
        boolean relativeXSupported = inputDevice.getMotionRange(MotionEvent.AXIS_RELATIVE_X) != null;
        boolean relativeYSupported = inputDevice.getMotionRange(MotionEvent.AXIS_RELATIVE_Y) != null;
        mDeviceSupportsRelativeAxis = relativeXSupported && relativeYSupported;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (!CallbackBridge.isGrabbing() // Only capture if not in menu and user said so
        && !PREF_MOUSE_GRAB_FORCE) {
            return;
        }
        if (hasFocus && Tools.isAndroid8OrHigher()) mHostView.requestPointerCapture();
    }

    public void detach() {
        mHostView.setOnCapturedPointerListener(null);
        mHostView.getViewTreeObserver().removeOnWindowFocusChangeListener(this);
    }
}
