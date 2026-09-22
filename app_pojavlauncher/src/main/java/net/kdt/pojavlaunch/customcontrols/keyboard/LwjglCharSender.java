package net.kdt.pojavlaunch.customcontrols.keyboard;

import static org.lwjgl.glfw.CallbackBridge.sendKeyPress;

import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.MainActivity;

import org.lwjgl.glfw.CallbackBridge;

/** Sends keys via the CallBackBridge */
public class LwjglCharSender implements CharacterSenderStrategy {
    @Override
    public void sendBackspace() {
        CallbackBridge.sendKeycode(LwjglGlfwKeycode.GLFW_KEY_BACKSPACE, '\u0008', 0, 0, true);
        CallbackBridge.sendKeycode(LwjglGlfwKeycode.GLFW_KEY_BACKSPACE, '\u0008', 0, 0, false);
    }

    @Override
    public void sendEnter() {
        MainActivity.trackChatStateKey(LwjglGlfwKeycode.GLFW_KEY_ENTER, true);
        sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_ENTER);
    }

    @Override
    public void sendChar(char character) {
        MainActivity.trackChatStateChar(character);
        CallbackBridge.sendChar(character, 0);
    }
}
