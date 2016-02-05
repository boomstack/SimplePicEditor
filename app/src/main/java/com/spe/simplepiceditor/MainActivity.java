package com.spe.simplepiceditor;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.romainpiel.shimmer.Shimmer;
import com.romainpiel.shimmer.ShimmerTextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private static int RESULT_LOAD_IMAGE = 1;
    //图片
    private Bitmap bitmap = null;

    //info
    private Point centerPoint = null;
    private int bmpHeight;
    private int bmpWidth;
    private int screenHeight;
    private int screenWidth;
    private int canvasHeight;
    private int canvasWidth;
    private int topPanelHeight;
    private int bottomPanelHeight;

    //布局实例
    private LinearLayout topLinear = null;
    private LinearLayout bottomLinear = null;

    //绘制模版
    private RectF dst = null;

    //主画布
    private MainCanvas mainCanvas = null;

    //辅助变量
    private Timer timer = null;
    private final static int randTop = 10000;
    private TextView txtWidth, txtColor, txtStyle;
    private Context context;

    private LinkedList<Step> undoStack = null;
    private LinkedList<Step> redoStack = null;

    ShimmerTextView tv;
    Shimmer shimmer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        initView();
        initData();
    }

    private void initView() {
        mainCanvas = (MainCanvas) findViewById(R.id.maincanvas);
        topLinear = (LinearLayout) findViewById(R.id.topPanel);
        bottomLinear = (LinearLayout) findViewById(R.id.bottomPanel);
        txtWidth = (TextView) findViewById(R.id.width);
        txtColor = (TextView) findViewById(R.id.color);
        txtStyle = (TextView) findViewById(R.id.style);
        tv = (ShimmerTextView) findViewById(R.id.shimmer_tv);
        if (shimmer != null && shimmer.isAnimating()) {
            shimmer.cancel();
        } else {
            shimmer = new Shimmer();
            shimmer.start(tv);
        }
    }

    private void initData() {
        context = this;
        calcuateScreenParams();
        calculateLinear();
        txtWidth.setText(PaintInfo.strokewidth + "");
        txtColor.setText("blue");
        txtStyle.setText("freehand");
        undoStack = mainCanvas.getUndoStack();
        redoStack = mainCanvas.getRedoStack();
    }

    /**
     * 回调方法
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && null != data) {
            Uri selectedImage = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};

            Cursor cursor = getContentResolver().query(selectedImage,
                    filePathColumn, null, null, null);
            if (null != cursor) {
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);

                bitmap = BitmapFactory.decodeFile(cursor.getString(columnIndex));
                if (bitmap.getHeight() > 4 * screenHeight || bitmap.getWidth() > 4 * screenWidth) {
                    System.out.println("level-00");
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 10;
                    bitmap = BitmapFactory.decodeFile(cursor.getString(columnIndex), options);
                }
                if (bitmap.getHeight() > 3 * screenHeight || bitmap.getWidth() > 3 * screenWidth) {
                    System.out.println("level-01");
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 7;
                    bitmap = BitmapFactory.decodeFile(cursor.getString(columnIndex), options);
                } else if (bitmap.getHeight() > 2 * screenHeight || bitmap.getWidth() > 2 * screenWidth) {
                    System.out.println("level-02");
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 3;
                    bitmap = BitmapFactory.decodeFile(cursor.getString(columnIndex), options);
                } else if (bitmap.getHeight() > screenHeight || bitmap.getWidth() > screenWidth) {
                    System.out.println("level-03");
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    bitmap = BitmapFactory.decodeFile(cursor.getString(columnIndex), options);
                }
                cursor.close();
                sendBitmap();
            }
        }
    }

    /**
     * 按钮事件处理
     */
    //导入图片
    public void loadImage(View view) {
        undoStack.clear();
        redoStack.clear();
        mainCanvas.invalidate();
        Intent i = new Intent(
                Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(i, RESULT_LOAD_IMAGE);
    }

    //打开弹出框
    public void paintsetting(View view) {
        showPopupWindow(view);
    }

    public void undo(View view) {
        if (!undoStack.isEmpty()) {
            Step s = undoStack.pop();
            redoStack.push(s);
        }
        mainCanvas.invalidate();
    }

    public void redo(View view) {
        if (!redoStack.isEmpty()) {
            Step s = redoStack.pop();
            undoStack.push(s);
        }
        mainCanvas.invalidate();
    }

    public void saveCanvas() {
        Random rand = new Random();
        int picNum = rand.nextInt(randTop);
        View content = mainCanvas;
        content.setDrawingCacheEnabled(true);
        content.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        Bitmap bitmap = content.getDrawingCache();
        File file = new File("/sdcard/spe/");
        if (!file.exists()) {
            file.mkdirs();
        }
        FileOutputStream ostream;
        try {
            file.createNewFile();
            System.out.println("file path: " + file.getPath());
            ostream = new FileOutputStream(file.getPath() + "/no" + picNum + ".png");
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, ostream);
            ostream.flush();
            ostream.close();
            showImageSavedToast();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "unknow error", Toast.LENGTH_SHORT).show();
        }
    }

    public void showImageSavedToast() {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.toastlayout, (ViewGroup) findViewById(R.id.toast_root));
        ImageView image = (ImageView) layout.findViewById(R.id.image_success);
        image.setImageResource(R.drawable.checked);
        Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.BOTTOM, 0, 0);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.show();
    }


    public void colorWhite(View view) {
        PaintInfo.paintColor = Color.WHITE;
        txtColor.setText("white");
    }

    public void colorBlack(View view) {
        PaintInfo.paintColor = Color.BLACK;
        txtColor.setText("black");
    }

    public void colorOrange(View view) {
        PaintInfo.paintColor = Color.YELLOW;
        txtColor.setText("orange");
    }

    public void colorYellow(View view) {
        PaintInfo.paintColor = Color.YELLOW;
        txtColor.setText("yellow");
    }

    public void colorGreen(View view) {
        PaintInfo.paintColor = Color.CYAN;
        txtColor.setText("green");
    }

    public void colorBlue(View view) {
        PaintInfo.paintColor = Color.BLUE;
        txtColor.setText("blue");
    }

    public void colorPurple(View view) {
        PaintInfo.paintColor = Color.MAGENTA;
        txtColor.setText("purple");
    }

    public void colorRed(View view) {
        PaintInfo.paintColor = Color.RED;
        txtColor.setText("red");
    }

    public void shapeFreehand(View view) {
        PaintInfo.drawType = DrawType.DRAW_FREEHAND;
        txtStyle.setText("freehand");
    }

    public void shapeRect(View view) {
        PaintInfo.drawType = DrawType.DRAW_RECT;
        txtStyle.setText("rectangle");
    }

    public void shapeOval(View view) {
        PaintInfo.drawType = DrawType.DRAW_OVAL;
        txtStyle.setText("oval");
    }

    /**
     * 辅助函数
     */
    //获取LinearLayout参数
    private void calculateLinear() {
        final Handler myHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == 1) {
                    if (topLinear.getWidth() != 0) {
                        topPanelHeight = topLinear.getHeight();
                        bottomPanelHeight = bottomLinear.getHeight();
                        canvasHeight = screenHeight - topPanelHeight - bottomPanelHeight;
                        canvasWidth = screenWidth;
                        centerPoint = new Point(canvasWidth / 2, canvasHeight / 2);
                        //取消定时器
                        timer.cancel();
                    }
                }
            }
        };
        timer = new Timer();
        TimerTask task = new TimerTask() {
            public void run() {
                Message message = new Message();
                message.what = 1;
                myHandler.sendMessage(message);
            }
        };
        //延迟每次延迟10 毫秒 隔1秒执行一次
        timer.schedule(task, 10, 1000);
    }

    //向画布发送数据
    private void sendBitmap() {
        bmpHeight = bitmap.getHeight();
        bmpWidth = bitmap.getWidth();
        if (null != bitmap) {
            float realBH = bmpHeight;
            float realBW = bmpWidth;
            float tmp = 1.0f;
            Bitmap bmp = null;
            if (bmpHeight > canvasHeight || bmpWidth > canvasWidth) {
                if (bmpHeight > bmpWidth) {
                    if (tmp > 0.1)
                        tmp = (float) canvasHeight / realBH - 0.1f;
                    else
                        tmp = (float) canvasHeight / realBH;
                } else {
                    tmp = (float) canvasWidth / realBW;
                }
                Matrix m = new Matrix();
                m.setScale(tmp, tmp);
                bmp = Bitmap.createBitmap(bitmap, 0, 0, bmpWidth, bmpHeight, m, true);
                bitmap.recycle();
                bitmap = null;
                realBH = bmp.getHeight();
                realBW = bmp.getWidth();
            }

            this.setDst(new RectF(centerPoint.x - realBW / 2, centerPoint.y - realBH / 2,
                    centerPoint.x + realBW / 2, centerPoint.y + realBH / 2));
            if (bmpHeight > canvasHeight || bmpWidth > canvasWidth) {
                this.mainCanvas.setBitmapLocation(bmp, dst);
            } else {
                this.mainCanvas.setBitmapLocation(bitmap, dst);
            }

        }
    }

    //打开ｗｉｎｄｏｗ

    private void showPopupWindow(View view) {
        View contentView = LayoutInflater.from(this).inflate(
                R.layout.layout_popupwindow, null);
        final PopupWindow popupWindow = new PopupWindow(contentView,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setTouchable(true);
        popupWindow.setBackgroundDrawable(getResources().getDrawable(
                R.drawable.popupbg));
        popupWindow.showAsDropDown(view);
        Button btn_save = (Button) contentView.findViewById(R.id.savepic);
        btn_save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCanvas();
            }
        });
        final SeekBar sb = (SeekBar) contentView.findViewById(R.id.seekBar);
        final TextView curWidth = (TextView) contentView.findViewById(R.id.curwidth);
        sb.setProgress(PaintInfo.strokewidth);
        curWidth.setText(" " + PaintInfo.strokewidth + " ");
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                curWidth.setText(" " + seekBar.getProgress() + " ");
                txtWidth.setText(" " + seekBar.getProgress() + " ");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(PaintInfo.strokewidth);
                txtWidth.setText(" " + seekBar.getProgress() + " ");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                PaintInfo.strokewidth = seekBar.getProgress();
                txtWidth.setText(" " + seekBar.getProgress() + " ");
            }
        });
    }

    //获取屏幕大小
    public void calcuateScreenParams() {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        setScreenWidth(dm.widthPixels);
        setScreenHeight(dm.heightPixels);
    }

    /**
     * getter setter
     */

    public void setScreenHeight(int i) {
        this.screenHeight = i;
    }

    public int getScreenHeight() {
        return this.screenHeight;
    }

    public void setScreenWidth(int i) {
        this.screenWidth = i;
    }

    public int getScreenWidth() {
        return this.screenWidth;
    }

    public void setDst(RectF rf) {
        this.dst = rf;
    }

    public RectF getDst() {
        return this.dst;
    }

}
