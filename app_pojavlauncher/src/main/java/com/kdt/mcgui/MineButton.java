package com.kdt.mcgui;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;

import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.R;

public class MineButton extends androidx.appcompat.widget.AppCompatButton {

    public MineButton(Context ctx) {
        this(ctx, null);
    }

    public MineButton(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        init();
    }

    public void init() {
        setTypeface(ResourcesCompat.getFont(
                getContext(),
                R.font.noto_sans_bold
        ));

        setBackground(ResourcesCompat.getDrawable(
                getResources(),
                R.drawable.cl_play_bg,
                getContext().getTheme()
        ));

        setTextColor(Color.WHITE);
        setGravity(Gravity.CENTER);
        setTextSize(
                android.util.TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen._16ssp)
        );

        setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.cl_play_icon,
                0,
                0,
                0
        );

        setCompoundDrawablePadding(
                getResources().getDimensionPixelSize(R.dimen._8sdp)
        );

        setPadding(
                getResources().getDimensionPixelSize(R.dimen._12sdp),
                0,
                getResources().getDimensionPixelSize(R.dimen._12sdp),
                0
        );

        setAllCaps(true);
        setStateListAnimator(null);
    }
}
