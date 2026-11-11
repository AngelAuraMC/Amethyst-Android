package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import net.kdt.pojavlaunch.R;

public final class NovaCraftAppearance {
    private static final String KEY_THEME = "novacraft_theme";
    private static final String KEY_BUTTON_STYLE = "novacraft_button_style";
    private static final String KEY_BACKGROUND = "novacraft_background_texture";
    private static final String KEY_FONT = "novacraft_font_style";
    private static final String KEY_DENSITY = "novacraft_home_density";
    private static final String KEY_ANIMATION = "novacraft_animated_background";
    private static final String KEY_LIGHTWEIGHT = "novacraft_lightweight_mode";

    private NovaCraftAppearance() { }

    public static boolean shouldAnimate(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(KEY_ANIMATION, true)
                && !prefs.getBoolean(KEY_LIGHTWEIGHT, false)
                && "haze".equals(prefs.getString(KEY_BACKGROUND, "haze"));
    }

    public static void applyHome(View root, Context context) {
        if (root == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Palette palette = getPalette(prefs.getString(KEY_THEME, "cyan"));
        String buttonStyle = prefs.getString(KEY_BUTTON_STYLE, "rounded");
        String backgroundStyle = prefs.getString(KEY_BACKGROUND, "haze");
        String fontStyle = prefs.getString(KEY_FONT, "system");
        String density = prefs.getString(KEY_DENSITY, "comfortable");
        boolean lightweight = prefs.getBoolean(KEY_LIGHTWEIGHT, false);

        root.setBackgroundColor(palette.background);

        View texture = root.findViewById(R.id.novacraft_background_texture);
        if (texture != null) {
            if ("plain".equals(backgroundStyle)) {
                texture.setVisibility(View.GONE);
            } else {
                texture.setVisibility(View.VISIBLE);
                if ("grid".equals(backgroundStyle)) {
                    texture.setBackground(new GridTextureDrawable(palette.background, palette.accent,
                            context.getResources().getDisplayMetrics().density));
                    texture.setAlpha(lightweight ? 0.22f : 0.38f);
                } else {
                    GradientDrawable haze = new GradientDrawable(
                            GradientDrawable.Orientation.TL_BR,
                            new int[]{palette.surface, palette.background, palette.surfaceRaised});
                    texture.setBackground(haze);
                    texture.setAlpha(lightweight ? 0.28f : 0.48f);
                }
            }
        }

        setText(root, R.id.novacraft_brand_title, palette.text);
        setText(root, R.id.novacraft_brand_tagline, palette.muted);
        setText(root, R.id.novacraft_hero_title, palette.text);
        setText(root, R.id.novacraft_hero_body, palette.muted);
        setText(root, R.id.novacraft_quick_tools_title, palette.muted);
        setText(root, R.id.novacraft_home_footer, palette.muted);
        setText(root, R.id.novacraft_install_label, palette.muted);

        View hero = root.findViewById(R.id.novacraft_hero_card);
        if (hero != null) {
            GradientDrawable heroShape = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{palette.surfaceRaised, palette.surface});
            heroShape.setCornerRadius(dp(context, 22));
            heroShape.setStroke(dp(context, 1), withAlpha(palette.accent, 115));
            hero.setBackground(heroShape);
        }

        View badge = root.findViewById(R.id.novacraft_hero_badge);
        if (badge != null) {
            badge.setBackground(rounded(withAlpha(palette.accent, 34), palette.accent, dp(context, 18)));
            if (badge instanceof TextView) ((TextView) badge).setTextColor(palette.accent);
        }

        View accent = root.findViewById(R.id.novacraft_hero_accent);
        if (accent != null) accent.setBackground(rounded(palette.accent, palette.accent, dp(context, 3)));

        View ambient = root.findViewById(R.id.novacraft_ambient_glow);
        if (ambient != null) {
            boolean showAmbient = "haze".equals(backgroundStyle);
            ambient.setVisibility(showAmbient && !lightweight ? View.VISIBLE : View.INVISIBLE);
            ambient.setAlpha(prefs.getBoolean(KEY_ANIMATION, true) && !lightweight ? 0.24f : 0.14f);
        }

        View panel = root.findViewById(R.id._background_display_view);
        if (panel != null) panel.setBackground(rounded(palette.surface, palette.border, dp(context, 22)));

        int[] shortcutIds = {
                R.id.news_button, R.id.discord_button, R.id.custom_control_button,
                R.id.install_jar_button, R.id.share_logs_button, R.id.open_files_button
        };
        int buttonHeight = "compact".equals(density) ? 48
                : ("spacious".equals(density) ? 64 : 56);
        for (int id : shortcutIds) {
            View candidate = root.findViewById(id);
            if (candidate instanceof Button) {
                Button button = (Button) candidate;
                button.setBackground(makeToolButton(palette, buttonStyle, context));
                button.setTextColor(palette.text);
                ViewGroup.LayoutParams lp = button.getLayoutParams();
                if (lp != null) {
                    lp.height = dp(context, buttonHeight);
                    button.setLayoutParams(lp);
                }
                tintCompoundDrawables(button, palette.accent);
            }
        }

        View version = root.findViewById(R.id.mc_version_spinner);
        if (version != null) {
            version.setBackground(rounded(palette.surfaceRaised, palette.border, dp(context, 15)));
            if (version instanceof TextView) ((TextView) version).setTextColor(palette.text);
        }

        ImageButton editProfile = root.findViewById(R.id.edit_profile_button);
        if (editProfile != null) {
            editProfile.setBackground(rounded(palette.surfaceRaised, palette.border, dp(context, 15)));
            editProfile.setColorFilter(palette.accent);
        }

        Button play = root.findViewById(R.id.play_button);
        if (play != null) {
            play.setBackground(makePlayButton(palette, context));
            play.setTextColor(palette.onAccent);
            play.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        }

        View heroCard = root.findViewById(R.id.novacraft_hero_card);
        if (heroCard != null) heroCard.setMinimumHeight(dp(context,
                "compact".equals(density) ? 148 : ("spacious".equals(density) ? 184 : 166)));

        View scroll = root.findViewById(R.id.novacraft_home_scroll);
        if (scroll != null) {
            int pad = dp(context, "compact".equals(density) ? 12 : 18);
            scroll.setPadding(pad, dp(context, 15), pad, dp(context, 16));
        }

        Typeface bodyTypeface = getTypeface(fontStyle, false);
        Typeface boldTypeface = getTypeface(fontStyle, true);
        int[] bodyIds = {R.id.novacraft_brand_tagline, R.id.novacraft_hero_body, R.id.novacraft_home_footer};
        for (int id : bodyIds) {
            TextView text = root.findViewById(id);
            if (text != null) text.setTypeface(bodyTypeface);
        }
        int[] boldIds = {R.id.novacraft_brand_title, R.id.novacraft_hero_title, R.id.novacraft_quick_tools_title};
        for (int id : boldIds) {
            TextView text = root.findViewById(id);
            if (text != null) text.setTypeface(boldTypeface);
        }
    }

    public static void applyHeader(Activity activity) {
        if (activity == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        Palette palette = getPalette(prefs.getString(KEY_THEME, "cyan"));
        View account = activity.findViewById(R.id.account_spinner);
        if (account != null) {
            account.setBackground(rounded(palette.surface, palette.border, dp(activity, 15)));
            if (account instanceof TextView) ((TextView) account).setTextColor(palette.text);
        }
        ImageButton settings = activity.findViewById(R.id.setting_button);
        if (settings != null) {
            settings.setBackground(rounded(palette.surfaceRaised, palette.border, dp(activity, 15)));
            settings.setColorFilter(palette.accent);
        }
    }

    private static Typeface getTypeface(String style, boolean bold) {
        int weight = bold ? Typeface.BOLD : Typeface.NORMAL;
        if ("mono".equals(style)) return Typeface.create(Typeface.MONOSPACE, weight);
        if ("condensed".equals(style)) return Typeface.create("sans-serif-condensed", weight);
        return Typeface.create("sans-serif", weight);
    }

    private static void setText(View root, int id, int color) {
        TextView text = root.findViewById(id);
        if (text != null) text.setTextColor(color);
    }

    private static void tintCompoundDrawables(TextView view, int color) {
        Drawable[] icons = view.getCompoundDrawablesRelative();
        for (int i = 0; i < icons.length; i++) {
            if (icons[i] != null) {
                icons[i] = icons[i].mutate();
                icons[i].setTint(color);
            }
        }
        view.setCompoundDrawablesRelative(icons[0], icons[1], icons[2], icons[3]);
    }

    private static Drawable makeToolButton(Palette palette, String style, Context context) {
        StateListDrawable selector = new StateListDrawable();
        int radius = dp(context, 15);
        int normalFill;
        int normalStroke;
        if ("outline".equals(style)) {
            normalFill = withAlpha(palette.surface, 35);
            normalStroke = palette.accent;
        } else if ("solid".equals(style)) {
            normalFill = withAlpha(palette.accent, 46);
            normalStroke = withAlpha(palette.accent, 150);
        } else {
            normalFill = palette.surface;
            normalStroke = palette.border;
        }
        selector.addState(new int[]{-android.R.attr.state_enabled},
                rounded(withAlpha(palette.surface, 120), palette.border, radius));
        selector.addState(new int[]{android.R.attr.state_pressed},
                rounded(withAlpha(palette.accent, 65), palette.accent, radius));
        selector.addState(new int[]{},
                rounded(normalFill, normalStroke, radius));
        return selector;
    }

    private static Drawable makePlayButton(Palette palette, Context context) {
        StateListDrawable selector = new StateListDrawable();
        int radius = dp(context, 17);
        selector.addState(new int[]{-android.R.attr.state_enabled},
                rounded(withAlpha(palette.accent, 90), withAlpha(palette.accent, 90), radius));
        selector.addState(new int[]{android.R.attr.state_pressed},
                rounded(palette.accentPressed, palette.accent, radius));
        selector.addState(new int[]{},
                rounded(palette.accent, palette.accent, radius));
        return selector;
    }

    private static GradientDrawable rounded(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(Math.max(1, radius / 15), stroke);
        return drawable;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((Math.max(0, Math.min(255, alpha))) << 24);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static Palette getPalette(String name) {
        if ("violet".equals(name)) {
            return new Palette(Color.rgb(15, 12, 24), Color.rgb(29, 23, 43),
                    Color.rgb(40, 32, 60), Color.rgb(67, 53, 91),
                    Color.rgb(248, 243, 255), Color.rgb(184, 170, 207),
                    Color.rgb(183, 154, 255), Color.rgb(128, 99, 205), Color.rgb(25, 17, 43));
        }
        if ("amber".equals(name)) {
            return new Palette(Color.rgb(20, 16, 9), Color.rgb(36, 27, 15),
                    Color.rgb(49, 37, 23), Color.rgb(83, 64, 40),
                    Color.rgb(255, 247, 232), Color.rgb(205, 186, 157),
                    Color.rgb(255, 196, 107), Color.rgb(184, 130, 46), Color.rgb(39, 24, 5));
        }
        return new Palette(Color.rgb(10, 17, 23), Color.rgb(19, 33, 42),
                Color.rgb(26, 44, 53), Color.rgb(41, 65, 75),
                Color.rgb(244, 250, 252), Color.rgb(145, 166, 177),
                Color.rgb(39, 214, 179), Color.rgb(21, 155, 130), Color.rgb(6, 23, 17));
    }

    private static final class Palette {
        final int background, surface, surfaceRaised, border, text, muted, accent, accentPressed, onAccent;
        Palette(int background, int surface, int surfaceRaised, int border, int text,
                int muted, int accent, int accentPressed, int onAccent) {
            this.background = background;
            this.surface = surface;
            this.surfaceRaised = surfaceRaised;
            this.border = border;
            this.text = text;
            this.muted = muted;
            this.accent = accent;
            this.accentPressed = accentPressed;
            this.onAccent = onAccent;
        }
    }

    private static final class GridTextureDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int background;
        private final int accent;
        private final float density;
        private int alpha = 255;

        GridTextureDrawable(int background, int accent, float density) {
            this.background = background;
            this.accent = accent;
            this.density = density;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, density));
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(background);
            paint.setAlpha(alpha);
            canvas.drawRect(bounds, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(accent);
            paint.setAlpha(Math.round(alpha * 0.12f));
            float step = 34f * density;
            for (float x = bounds.left; x <= bounds.right; x += step) {
                canvas.drawLine(x, bounds.top, x, bounds.bottom, paint);
            }
            for (float y = bounds.top; y <= bounds.bottom; y += step) {
                canvas.drawLine(bounds.left, y, bounds.right, y, paint);
            }
        }

        @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
