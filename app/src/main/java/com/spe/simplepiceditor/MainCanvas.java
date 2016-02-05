package com.spe.simplepiceditor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Stack;


public class MainCanvas extends View implements View.OnTouchListener {

    private RectF dst = null;
    private Bitmap bmpSource = null;
    private LinkedList<Step> undoStack = null;
    private LinkedList<Step> redoStack = null;
    private boolean isMoving = false;
    Step step = null;
    private Canvas canvas = null;


    float x1, y1, x2, y2;

    public MainCanvas(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnTouchListener(this);
        undoStack = new LinkedList<Step>();
        redoStack = new LinkedList<Step>();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        this.canvas = canvas;
        if (null != bmpSource) {
            canvas.drawBitmap(this.bmpSource, null, this.dst, null);
            System.out.println("dst parms: " + dst.left + " " + dst.top + " " + dst.right + " " + dst.bottom);
            canvas.clipRect(dst);
            System.out.println("picture has been loaded." + "  ---" + canvas.getHeight() + " " + canvas.getWidth());
        }
        if (isMoving && null != step) {
            canvas.drawPath(step.path, step.paint);
        }
        if (!undoStack.isEmpty()) {
            Iterator<Step> it = undoStack.iterator();
            while (it.hasNext()) {
                Step s = it.next();
                canvas.drawPath(s.path, s.paint);
            }
        }
    }

    public void setBitmapLocation(Bitmap bitmap, RectF rectF) {
        bmpSource = bitmap;
        this.dst = rectF;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                x1 = event.getX();
                y1 = event.getY();
                step = new Step();
                step.paint.setColor(PaintInfo.paintColor);
                if (PaintInfo.drawType == DrawType.DRAW_FREEHAND) {
                    step.path.moveTo(x1, y1);
                } else if (PaintInfo.drawType == DrawType.DRAW_OVAL) {
                    RectF r = new RectF(x1, y1, x2, y2);
                    step.path.addOval(r, Path.Direction.CCW);
                } else if (PaintInfo.drawType == DrawType.DRAW_RECT) {
                    RectF r = new RectF(x1, y1, x2, y2);
                    step.path.addRoundRect(r, 1, 1, Path.Direction.CCW);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                isMoving = true;
                x2 = event.getX();
                y2 = event.getY();
                if (PaintInfo.drawType == DrawType.DRAW_FREEHAND) {
                    step.path.lineTo(x2, y2);
                } else if (PaintInfo.drawType == DrawType.DRAW_OVAL) {
                    RectF r = new RectF(x1, y1, x2, y2);
                    step.path.rewind();
                    step.path.addOval(r, Path.Direction.CCW);
                } else if (PaintInfo.drawType == DrawType.DRAW_RECT) {
                    step.path.rewind();
                    RectF r = new RectF(x1, y1, x2, y2);
                    step.path.addRoundRect(r, 1, 1, Path.Direction.CCW);
                }
                this.invalidate();
                break;
            case MotionEvent.ACTION_UP:
                isMoving = false;
                undoStack.push(step);
                this.invalidate();
                break;
            default:
                break;
        }
        return true;
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public LinkedList<Step> getUndoStack() {
        return undoStack;
    }

    public LinkedList<Step> getRedoStack() {
        return redoStack;
    }
}
