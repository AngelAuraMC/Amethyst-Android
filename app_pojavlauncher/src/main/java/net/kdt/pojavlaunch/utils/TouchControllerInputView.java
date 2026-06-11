package net.kdt.pojavlaunch.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorBoundsInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.inputmethod.EditorInfoCompat;

import top.fifthlight.touchcontroller.proxy.client.LauncherProxyClient;
import top.fifthlight.touchcontroller.proxy.message.FloatRect;
import top.fifthlight.touchcontroller.proxy.message.input.TextInputState;
import top.fifthlight.touchcontroller.proxy.message.input.TextInputStateKt;
import top.fifthlight.touchcontroller.proxy.message.input.TextRange;

import static top.fifthlight.touchcontroller.proxy.message.input.TextInputStateKt.getCompositionText;
import static top.fifthlight.touchcontroller.proxy.message.input.TextInputStateKt.getSelectionText;

import net.kdt.pojavlaunch.EfficientAndroidLWJGLKeycode;
import net.kdt.pojavlaunch.customcontrols.keyboard.CharacterSenderStrategy;

public class TouchControllerInputView extends View {
    public boolean disableFullScreenInput = false;
    private LauncherProxyClient client;
    private int width = -1;
    private int height = -1;
    @Nullable
    public CharacterSenderStrategy characterSenderStrategy;
    private final InputMethodManager inputMethodManager;

    private TextInputState inputState;
    private FloatRect cursorRect;
    private FloatRect inputAreaRect;
    private InputConnectionImpl inputConnection;
    private final LauncherProxyClient.InputHandler inputHandler;

    public TouchControllerInputView(Context context) {
        this(context, null);
    }

    public TouchControllerInputView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TouchControllerInputView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        inputMethodManager = ContextCompat.getSystemService(context, InputMethodManager.class);
        if (inputMethodManager == null) {
            throw new IllegalStateException("No InputMethodManager service");
        }

        inputHandler = new LauncherProxyClient.InputHandler() {
            @Override
            public void updateState(TextInputState textInputState) {
                post(() -> {
                    TextInputState prevState = inputState;
                    inputState = textInputState;
                    if (textInputState != null) {
                        setVisibility(VISIBLE);
                        setFocusable(true);
                    }
                    if (prevState == null && textInputState != null) {
                        setFocusableInTouchMode(true);
                        clearFocus();
                        requestFocus();
                        inputMethodManager.showSoftInput(
                                TouchControllerInputView.this,
                                InputMethodManager.SHOW_IMPLICIT
                        );
                    } else if (prevState != null && textInputState == null) {
                        clearFocus();
                        inputMethodManager.hideSoftInputFromWindow(
                                getWindowToken(),
                                InputMethodManager.HIDE_IMPLICIT_ONLY
                        );
                    }
                    if (textInputState != null) {
                        if (inputConnection != null) {
                            inputConnection.updateState(textInputState);
                        }
                    } else {
                        setVisibility(GONE);
                        setFocusable(false);
                    }
                });
            }

            @Override
            public void updateCursor(FloatRect cursorRect) {
                TouchControllerInputView.this.cursorRect = cursorRect;
                updateCursorAnchorInfo();
            }

            @Override
            public void updateArea(FloatRect inputAreaRect) {
                TouchControllerInputView.this.inputAreaRect = inputAreaRect;
                updateCursorAnchorInfo();
            }
        };
    }

    private static boolean isEmpty(TextRange range) {
        return range.getLength() == 0;
    }

    private static String removeRange(String text, TextRange range) {
        return text.substring(0, range.getStart()) + text.substring(range.getEnd());
    }

    private static String substring(String text, TextRange range) {
        return text.substring(range.getStart(), range.getEnd());
    }

    private static String replaceRange(String text, TextRange range, CharSequence newText) {
        return text.substring(0, range.getStart()) + newText + text.substring(range.getEnd());
    }

    public LauncherProxyClient getClient() {
        return client;
    }

    public void setClient(LauncherProxyClient value) {
        LauncherProxyClient prev = this.client;
        if (prev != null) {
            prev.setInputHandler(null);
        }
        this.client = value;
        if (value != null) {
            value.setInputHandler(inputHandler);
        }
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Nullable
    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        LauncherProxyClient currentClient = client;
        TextInputState currentState = inputState;
        if (currentClient == null || currentState == null) {
            return null;
        }
        outAttrs.initialSelStart = currentState.getSelection().getStart();
        outAttrs.initialSelEnd = currentState.getSelection().getEnd();
        EditorInfoCompat.setInitialSurroundingText(outAttrs, currentState.getText());
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL;
        outAttrs.imeOptions = disableFullScreenInput ? EditorInfo.IME_FLAG_NO_FULLSCREEN : 0;

        inputConnection = new InputConnectionImpl(currentState, currentClient::updateTextInputState);
        return inputConnection;
    }

    private void updateCursorAnchorInfo() {
        TextInputState inputState = this.inputState;
        if (inputConnection == null || inputState == null) {
            return;
        }
        CursorAnchorInfo.Builder builder = new CursorAnchorInfo.Builder();
        builder.setSelectionRange(inputState.getSelection().getStart(), inputState.getSelection().getEnd());
        if (!isEmpty(inputState.getComposition())) {
            builder.setComposingText(inputState.getComposition().getStart(), getCompositionText(inputState));
        }
        if (cursorRect != null) {
            builder.setInsertionMarkerLocation(
                    cursorRect.getLeft() * width,
                    cursorRect.getTop() * height,
                    (cursorRect.getLeft() + cursorRect.getWidth()) * width,
                    (cursorRect.getTop() + cursorRect.getHeight()) * height,
                    CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION
            );
        }
        if (inputAreaRect != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                EditorBoundsInfo editorBoundsInfo = new EditorBoundsInfo.Builder()
                        .setEditorBounds(
                                new RectF(
                                        inputAreaRect.getLeft() * width,
                                        inputAreaRect.getTop() * height,
                                        (inputAreaRect.getLeft() + inputAreaRect.getWidth()) * width,
                                        (inputAreaRect.getTop() + inputAreaRect.getHeight()) * height
                                )
                        )
                        .build();
                builder.setEditorBoundsInfo(editorBoundsInfo);
            }
        }
        builder.setMatrix(getMatrix());
        inputMethodManager.updateCursorAnchorInfo(this, builder.build());
    }

    private class InputConnectionImpl implements InputConnection {
        private TextInputState state;
        private final StateChangedListener onStateChanged;
        private final ClipboardManager clipboardManager;
        private int inBatchEdit = 0;
        private TextInputState delayedNewStateByBatchEdit;
        private Integer extractTextToken;
        private boolean hasZeroExtractToken = false;

        public InputConnectionImpl(TextInputState initialState, StateChangedListener onStateChanged) {
            this.state = initialState;
            this.onStateChanged = onStateChanged;
            this.clipboardManager = ContextCompat.getSystemService(getContext(), ClipboardManager.class);
            refreshState();
            onStateChanged.onStateChanged(initialState);
        }

        private TextRange minus(TextRange range1, TextRange range2) {
            int e1 = range1.getEnd();
            int s2 = range2.getStart();
            int e2 = range2.getEnd();

            int newStart = (range1.getStart() < s2) ? range1.getStart() : Math.max(range1.getStart(), e2) - range2.getLength();

            int part1 = Math.min(e1, s2) - range1.getStart();
            int part2 = e1 - Math.max(range1.getStart(), e2);

            int newLength = Math.max(0, part1) + Math.max(0, part2);
            return new TextRange(newStart, newLength);
        }

        private void refreshState() {
            inputMethodManager.updateSelection(
                    TouchControllerInputView.this,
                    state.getSelection().getStart(),
                    state.getSelection().getEnd(),
                    state.getComposition().getStart(),
                    state.getComposition().getEnd()
            );
            ExtractedText extractedText = getExtractedText();
            if (hasZeroExtractToken) {
                inputMethodManager.updateExtractedText(TouchControllerInputView.this, 0, extractedText);
            }
            if (extractTextToken != null) {
                inputMethodManager.updateExtractedText(TouchControllerInputView.this, extractTextToken, extractedText);
            }
        }

        public void updateState(TextInputState newState) {
            if (inBatchEdit > 0) {
                delayedNewStateByBatchEdit = newState;
                return;
            }
            if (state.equals(newState)) {
                return;
            }
            if (!state.getText().equals(newState.getText())) {
                inputMethodManager.restartInput(TouchControllerInputView.this);
            }
            state = newState;
            refreshState();
        }

        private void updateState(TextInputStateUpdater updater) {
            updateState(true, updater);
        }

        private void updateState(boolean refresh, TextInputStateUpdater updater) {
            TextInputState newState = updater.update(state);
            state = newState;
            if (inBatchEdit == 0) {
                if (refresh) {
                    refreshState();
                }
                onStateChanged.onStateChanged(newState);
            }
        }

        @Override
        public boolean beginBatchEdit() {
            inBatchEdit++;
            return true;
        }

        @Override
        public boolean clearMetaKeyStates(int states) {
            return true;
        }

        @Override
        public void closeConnection() {
        }

        @Override
        public boolean commitCompletion(CompletionInfo text) {
            return true;
        }

        @Override
        public boolean commitContent(@NonNull InputContentInfo inputContentInfo, int flags, Bundle opts) {
            return false;
        }

        @Override
        public boolean commitCorrection(CorrectionInfo correctionInfo) {
            return false;
        }

        private TextInputState commitTextAsNewState(TextInputState currentState, CharSequence text, int newCursorPosition) {
            if (!isEmpty(currentState.getComposition())) {
                String newText = replaceRange(currentState.getText(), currentState.getComposition(), text);
                int finalCursorPosition = newCursorPosition > 0 ?
                        currentState.getComposition().getStart() + text.length() + newCursorPosition - 1 :
                        currentState.getComposition().getStart() - newCursorPosition;
                finalCursorPosition = Math.max(0, Math.min(finalCursorPosition, newText.length()));
                return new TextInputState(
                        newText,
                        TextRange.Companion.getEMPTY(),
                        new TextRange(finalCursorPosition),
                        currentState.getSelectionLeft()
                );
            } else {
                String newText = replaceRange(currentState.getText(), currentState.getSelection(), text);
                int finalCursorPosition = newCursorPosition > 0 ?
                        currentState.getSelection().getStart() + text.length() + newCursorPosition - 1 :
                        currentState.getSelection().getStart() - newCursorPosition;
                finalCursorPosition = Math.max(0, Math.min(finalCursorPosition, newText.length()));
                return new TextInputState(
                        newText,
                        TextRange.Companion.getEMPTY(),
                        new TextRange(finalCursorPosition),
                        currentState.getSelectionLeft()
                );
            }
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            int enterCount = 0;
            StringBuilder filteredTextBuilder = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\n') {
                    enterCount++;
                } else {
                    filteredTextBuilder.append(c);
                }
            }
            CharSequence filteredText = filteredTextBuilder.toString();

            if (filteredText.length() > 0) {
                updateState(currentState -> commitTextAsNewState(currentState, filteredText, newCursorPosition));
            }
            for (int i = 0; i < enterCount; i++) {
                if (characterSenderStrategy != null) {
                    characterSenderStrategy.sendEnter();
                }
            }
            return true;
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            updateState(currentState -> {
                int limitedBeforeLength = Math.min(beforeLength, currentState.getSelection().getStart());
                int limitedAfterLength = Math.min(afterLength, currentState.getText().length() - currentState.getSelection().getEnd());
                String beforeText = currentState.getText().substring(0, currentState.getSelection().getStart() - limitedBeforeLength);
                String selectedText = substring(currentState.getText(), currentState.getSelection());
                String afterText = currentState.getText().substring(currentState.getSelection().getEnd() + limitedAfterLength);
                TextRange removedLeftRange = new TextRange(currentState.getSelection().getStart() - limitedBeforeLength, limitedBeforeLength);
                TextRange removedRightRange = new TextRange(currentState.getSelection().getEnd(), limitedAfterLength);
                return new TextInputState(
                        beforeText + selectedText + afterText,
                        minus(minus(currentState.getComposition(), removedRightRange), removedLeftRange),
                        new TextRange(
                                beforeText.length(),
                                selectedText.length()
                        ),
                        currentState.getSelectionLeft()
                );
            });
            return true;
        }

        @Override
        public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
            TextInputState currentState = state;
            String text = currentState.getText();
            int selectionStart = currentState.getSelection().getStart();

            int remainingBefore = beforeLength;
            int charCountBefore = 0;
            int index = selectionStart - 1;

            while (remainingBefore > 0 && index >= 0) {
                int codePoint = Character.codePointBefore(text, index + 1);
                int charCount = Character.charCount(codePoint);
                charCountBefore += charCount;
                index -= charCount;
                remainingBefore--;
            }

            int remainingAfter = afterLength;
            int charCountAfter = 0;
            index = selectionStart;

            while (remainingAfter > 0 && index < text.length()) {
                int codePoint = Character.codePointAt(text, index);
                int charCount = Character.charCount(codePoint);
                charCountAfter += charCount;
                index += charCount;
                remainingAfter--;
            }

            return deleteSurroundingText(charCountBefore, charCountAfter);
        }

        @Override
        public boolean endBatchEdit() {
            inBatchEdit--;
            if (inBatchEdit == 0) {
                if (delayedNewStateByBatchEdit != null) {
                    updateState(delayedNewStateByBatchEdit);
                    delayedNewStateByBatchEdit = null;
                } else {
                    refreshState();
                    onStateChanged.onStateChanged(state);
                }
            }
            return inBatchEdit > 0;
        }

        @Override
        public boolean finishComposingText() {
            updateState(currentState -> new TextInputState(
                    currentState.getText(),
                    TextRange.Companion.getEMPTY(),
                    currentState.getSelection(),
                    currentState.getSelectionLeft()
            ));
            return true;
        }

        @Override
        public int getCursorCapsMode(int reqModes) {
            return state.getSelectionLeft() ?
                    TextUtils.getCapsMode(state.getText(), state.getSelection().getStart(), reqModes) :
                    TextUtils.getCapsMode(state.getText(), state.getSelection().getEnd(), reqModes);
        }

        private ExtractedText getExtractedText() {
            ExtractedText extractedText = new ExtractedText();
            extractedText.text = state.getText();
            extractedText.selectionStart = state.getSelection().getStart();
            extractedText.selectionEnd = state.getSelection().getEnd();
            extractedText.startOffset = 0;
            extractedText.partialStartOffset = -1;
            extractedText.partialEndOffset = 0;
            return extractedText;
        }

        @Override
        public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
            if (request.token == 0) {
                hasZeroExtractToken = true;
            } else {
                this.extractTextToken = request.token;
            }
            return getExtractedText();
        }

        @Nullable
        @Override
        public android.os.Handler getHandler() {
            return null;
        }

        @Nullable
        @Override
        public CharSequence getSelectedText(int flags) {
            return !isEmpty(state.getSelection()) ? substring(state.getText(), state.getSelection()) : null;
        }

        @Override
        public CharSequence getTextAfterCursor(int n, int flags) {
            int start = state.getSelection().getEnd();
            int end = Math.min(start + n, state.getText().length());
            return state.getText().substring(start, end);
        }

        @Override
        public CharSequence getTextBeforeCursor(int n, int flags) {
            int end = state.getSelection().getStart();
            int start = Math.max(end - n, 0);
            return state.getText().substring(start, end);
        }

        @Override
        public boolean performContextMenuAction(int id) {
            switch (id) {
                case android.R.id.selectAll:
                    updateState(state -> new TextInputState(
                            state.getText(),
                            new TextRange(0, state.getText().length()),
                            TextRange.Companion.getEMPTY(),
                            state.getSelectionLeft()
                    ));
                    break;
                case android.R.id.cut:
                    CharSequence cutText = getSelectionText(state);
                    updateState(state -> new TextInputState(
                            removeRange(state.getText(), state.getSelection()),
                            new TextRange(state.getSelection().getStart()),
                            minus(state.getComposition(), state.getSelection()),
                            state.getSelectionLeft()
                    ));
                    if (clipboardManager != null) {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText(null, cutText));
                    }
                    break;
                case android.R.id.copy:
                    if (clipboardManager != null) {
                        clipboardManager.setPrimaryClip(
                                ClipData.newPlainText(
                                        null,
                                        getSelectionText(state)
                                )
                        );
                    }
                    break;
                case android.R.id.paste:
                    if (clipboardManager != null && clipboardManager.getPrimaryClip() != null && clipboardManager.getPrimaryClip().getItemCount() > 0) {
                        CharSequence text = clipboardManager.getPrimaryClip().getItemAt(0).getText();
                        if (text != null) {
                            updateState(state -> commitTextAsNewState(state, text, 1));
                        }
                    }
                    break;
                default:
                    return false;
            }
            return true;
        }

        @Override
        public boolean performEditorAction(int editorAction) {
            return false;
        }

        @Override
        public boolean performPrivateCommand(String action, Bundle data) {
            return false;
        }

        @Override
        public boolean reportFullscreenMode(boolean enabled) {
            if (!inputMethodManager.isFullscreenMode()) {
                extractTextToken = null;
            }
            return true;
        }

        @Override
        public boolean requestCursorUpdates(int cursorUpdateMode) {
            return false;
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    return true;
                }
                if (characterSenderStrategy != null) {
                    characterSenderStrategy.sendEnter();
                }
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    return true;
                }
                if (event.isShiftPressed()) {
                    updateState(TextInputStateKt::doShiftLeft);
                } else {
                    updateState(TextInputStateKt::doArrowLeft);
                }
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    return true;
                }
                if (event.isShiftPressed()) {
                    updateState(TextInputStateKt::doShiftRight);
                } else {
                    updateState(TextInputStateKt::doArrowRight);
                }
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    return true;
                }
                updateState(TextInputStateKt::doBackspace);
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_FORWARD_DEL) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    return true;
                }
                updateState(TextInputStateKt::doDelete);
            } else {
                int index = EfficientAndroidLWJGLKeycode.getIndexByKey(event.getKeyCode());
                if (EfficientAndroidLWJGLKeycode.containsIndex(index)) {
                    EfficientAndroidLWJGLKeycode.execKey(event, index);
                }
            }
            return true;
        }

        @Override
        public boolean setComposingRegion(int start, int end) {
            updateState(false, currentState -> new TextInputState(
                    currentState.getText(),
                    new TextRange(start, end - start),
                    currentState.getSelection(),
                    currentState.getSelectionLeft()
            ));
            return true;
        }

        @Override
        public boolean setComposingText(CharSequence text, int newCursorPosition) {
            updateState(currentState -> {
                if (!isEmpty(currentState.getComposition())) {
                    String newText = replaceRange(currentState.getText(), currentState.getComposition(), text);
                    int finalCursorPosition = newCursorPosition > 0 ?
                            currentState.getComposition().getStart() + text.length() + newCursorPosition - 1 :
                            currentState.getComposition().getStart() - newCursorPosition;
                    finalCursorPosition = Math.max(0, Math.min(finalCursorPosition, newText.length()));
                    return new TextInputState(
                            newText,
                            new TextRange(currentState.getComposition().getStart(), text.length()),
                            new TextRange(finalCursorPosition),
                            currentState.getSelectionLeft()
                    );
                } else {
                    String newText = replaceRange(currentState.getText(), currentState.getSelection(), text);
                    int finalCursorPosition = newCursorPosition > 0 ?
                            currentState.getSelection().getStart() + text.length() + newCursorPosition - 1 :
                            currentState.getSelection().getStart() - newCursorPosition;
                    finalCursorPosition = Math.max(0, Math.min(finalCursorPosition, newText.length()));
                    return new TextInputState(
                            newText,
                            new TextRange(currentState.getSelection().getStart(), text.length()),
                            new TextRange(finalCursorPosition),
                            currentState.getSelectionLeft()
                    );
                }
            });
            return true;
        }

        @Override
        public boolean setSelection(int start, int end) {
            updateState(currentState -> new TextInputState(
                    currentState.getText(),
                    currentState.getComposition(),
                    new TextRange(start, end - start),
                    currentState.getSelectionLeft()
            ));
            return true;
        }
    }

    private interface StateChangedListener {
        void onStateChanged(TextInputState newState);
    }

    private interface TextInputStateUpdater {
        TextInputState update(TextInputState currentState);
    }
}