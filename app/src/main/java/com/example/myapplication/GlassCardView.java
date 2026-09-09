package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;

import com.google.android.material.card.MaterialCardView;

/** Translucent surface with a soft reflection and a bright, rounded glass edge. */
public final class GlassCardView extends MaterialCardView {
    private final Paint reflection = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    public GlassCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(getResources().getDisplayMetrics().density);
        setOutlineAmbientShadowColor(context.getColor(R.color.attendance_glass_shadow));
        setOutlineSpotShadowColor(context.getColor(R.color.attendance_glass_shadow));
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float inset = rim.getStrokeWidth() / 2f;
        bounds.set(inset, inset, width - inset, height - inset);
        reflection.setShader(new LinearGradient(0, 0, width, height,
                getContext().getColor(R.color.attendance_glass_reflection),
                android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, 0, width * 0.7f, height,
                getContext().getColor(R.color.attendance_glass_highlight),
                getContext().getColor(R.color.attendance_glass_stroke), Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = Math.max(0, getRadius() - rim.getStrokeWidth() / 2f);
        canvas.drawRoundRect(bounds, radius, radius, reflection);
        canvas.drawRoundRect(bounds, radius, radius, rim);
    }
}
