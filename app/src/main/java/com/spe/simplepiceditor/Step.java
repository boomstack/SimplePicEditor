package com.spe.simplepiceditor;

import android.graphics.Paint;
import android.graphics.Path;

public class Step {
    public Paint paint;
    public Path path;

    public Step() {
        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(PaintInfo.strokewidth);
        path = new Path();
    }
}
