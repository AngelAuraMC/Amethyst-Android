package net.kdt.pojavlaunch.prefs;

import static net.kdt.pojavlaunch.Tools.dialog;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputType;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.SwitchPreferenceCompat;

import net.kdt.pojavlaunch.R;

import java.util.Locale;
import java.util.Random;

public class MathQuestionPreference extends SwitchPreferenceCompat {
    private AlertDialog mathDialog;
    public MathQuestionPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        ((Application) context.getApplicationContext())
                .registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityPaused(@NonNull Activity activity) {
                        if (mathDialog != null && mathDialog.isShowing()) {
                            mathDialog.dismiss();
                        }
                    }

                    // Unused callbacks
                    public void onActivityCreated(@NonNull Activity a, Bundle b) {}
                    public void onActivityStarted(@NonNull Activity a) {}
                    public void onActivityResumed(@NonNull Activity a) {}
                    public void onActivityStopped(@NonNull Activity a) {}
                    public void onActivitySaveInstanceState(@NonNull Activity a, Bundle b) {}
                    public void onActivityDestroyed(@NonNull Activity a) {}
                });
    }

    // mapping hardcoded to english. we want people who can actually understand the message
    private static final String[] units = {
            "zero", "one", "two", "three", "four",
            "five", "six", "seven", "eight", "nine",
            "ten"
    };

    private static String intToWord(int number) {
        if (number >= 0 && number < units.length) {
            return units[number];
        } else {
            throw new IllegalArgumentException("This function only works for numbers 0 to 10");
        }
    }

    @Override
    protected void onClick() {
        if (isChecked()) { // Don't ask for braincells if turning off
            super.onClick();
            return;
        }

        Random random = new Random();
        int a = random.nextInt(10) + 1;
        int b = random.nextInt(10) + 1;
        int c = random.nextInt(10) + 1;
        int d = random.nextInt(10) + 1;
        final int answer = (a * b) + c - d;
        final Context ctx = getContext();
        final EditText input = new EditText(ctx);
        input.setInputType(InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_CLASS_NUMBER);

        mathDialog = new AlertDialog.Builder(ctx)
                .setTitle(R.string.sodium_math_warning_title)
                .setMessage(ctx.getString(R.string.sodium_math_warning_message, intToWord(a), intToWord(b), intToWord(c), intToWord(d)))
                .setView(input)
                .setPositiveButton(ctx.getText(android.R.string.ok), (dialog, which) -> {
                    try {
                        int userAnswer = Integer.parseInt(input.getText().toString());
                        if (userAnswer == answer) {
                            super.onClick();
                        } else {
                            dialog(ctx, "Wrong!", "You failed the math test!");
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(ctx, "Please enter a number.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();

        Button positiveButton = mathDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        positiveButton.setEnabled(false);
        int waitTime = 45;
        new CountDownTimer(waitTime * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long secondsLeft = (millisUntilFinished / 1000) + 1;
                positiveButton.setText(String.format(Locale.ROOT, "%s(%d)", ctx.getString(android.R.string.ok), secondsLeft));
            }

            @Override
            public void onFinish() {
                positiveButton.setText(ctx.getString(android.R.string.ok));
                positiveButton.setEnabled(true);
            }
        }.start();
    }
}
