package com.huanmeng.shiyan;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class PlayerActivity extends Activity implements View.OnTouchListener,
        SurfaceHolder.Callback, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;

    private SeekBar seekBar;
    private TextView tvCurrent, tvDuration, tvVideoTitle, tvGestureType, tvGestureValue;
    private ProgressBar gestureProgressBar;
    private LinearLayout gestureOverlay, controlPanel, llSpeedPanel;
    private RelativeLayout topPanel;
    
    private ImageView btnPrev, btnPlayPause, btnNext; 
    private Button btnRotate, btnSpeed;
    
    private TextView tvSpeed08, tvSpeed10, tvSpeed125, tvSpeed15, tvSpeed20;
    private Handler handler = new Handler();

    private ArrayList<String> videoList = new ArrayList<String>();
    private int currentVideoIndex = 0;
    private boolean isPrepared = false;
    private boolean wasPlayingBeforePause = true;
    private int savePosition = 0;
    private float currentSpeed = 1.0f;

    private float mStartX, mStartY;
    private int mStartVolume;
    private float mStartBrightness;
    private int mStartPosition;
    
    private static final float VOLUME_SENS = 0.055f; 
    private static final float BRIGHT_SENS = 0.008f; 
    
    private boolean isLeftSideBrightness = false;

    private int gestureMode = 0; 
    private boolean isMoved = false;
    private static final int TOUCH_THRESHOLD = 20;

    private boolean isLongPress = false;
    private static final long LONG_PRESS_TIME = 500;
    private boolean isControlShow = true;

    // 用于双击检测
    private long firstClickTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 强制全屏隐藏状态栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);

        if (savedInstanceState != null) {
            savePosition = savedInstanceState.getInt("pos", 0);
            wasPlayingBeforePause = savedInstanceState.getBoolean("was_playing", true);
            currentVideoIndex = savedInstanceState.getInt("video_index", 0);
        }
        setContentView(R.layout.activity_player);

        // 🌟 顶部栏固定内边距，避免刘海屏遮挡，不再依赖动态状态栏高度
        topPanel = (RelativeLayout) findViewById(R.id.topPanel);
        topPanel.setPadding(0, (int) (20 * getResources().getDisplayMetrics().density + 0.5f), 0, 0);

        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        seekBar = (SeekBar) findViewById(R.id.seekBar);
        tvCurrent = (TextView) findViewById(R.id.tvCurrent);
        tvDuration = (TextView) findViewById(R.id.tvDuration);
        tvVideoTitle = (TextView) findViewById(R.id.tvVideoTitle);
        tvGestureType = (TextView) findViewById(R.id.tvGestureType);
        tvGestureValue = (TextView) findViewById(R.id.tvGestureValue);
        gestureProgressBar = (ProgressBar) findViewById(R.id.gestureProgressBar);
        
        gestureOverlay = (LinearLayout) findViewById(R.id.gestureOverlay);
        controlPanel = (LinearLayout) findViewById(R.id.controlPanel);
        llSpeedPanel = (LinearLayout) findViewById(R.id.llSpeedPanel);

        btnPrev = (ImageView) findViewById(R.id.btnPrev);
        btnPlayPause = (ImageView) findViewById(R.id.btnPlayPause);
        btnNext = (ImageView) findViewById(R.id.btnNext);
        btnRotate = (Button) findViewById(R.id.btnRotate);
        btnSpeed = (Button) findViewById(R.id.btnSpeed);

        tvSpeed08 = (TextView) findViewById(R.id.tvSpeed08);
        tvSpeed10 = (TextView) findViewById(R.id.tvSpeed10);
        tvSpeed125 = (TextView) findViewById(R.id.tvSpeed125);
        tvSpeed15 = (TextView) findViewById(R.id.tvSpeed15);
        tvSpeed20 = (TextView) findViewById(R.id.tvSpeed20);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        Intent intent = getIntent();
        videoList = intent.getStringArrayListExtra("videoList");
        currentVideoIndex = intent.getIntExtra("videoIndex", 0);
        if (videoList == null) videoList = new ArrayList<String>();
        if (videoList.size() > 0) {
            String path = videoList.get(currentVideoIndex);
            tvVideoTitle.setText(path.substring(path.lastIndexOf("/") + 1));
        }

        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (mediaPlayer != null && isPrepared) {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.pause(); 
                        btnPlayPause.setImageResource(R.drawable.bofang); 
                        wasPlayingBeforePause = false;
                    } else {
                        if (mediaPlayer.getCurrentPosition() >= mediaPlayer.getDuration() - 500) {
                            mediaPlayer.seekTo(0); seekBar.setProgress(0); tvCurrent.setText("00:00");
                        }
                        mediaPlayer.start(); 
                        btnPlayPause.setImageResource(R.drawable.zantin); 
                        wasPlayingBeforePause = true;
                    }
                }
            }
        });
        
        btnPrev.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { playPrev(); } });
        btnNext.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { playNext(); } });
        
        btnRotate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                else setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        });

        btnSpeed.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (llSpeedPanel.getVisibility() == View.GONE) {
                    llSpeedPanel.setVisibility(View.VISIBLE);
                } else {
                    llSpeedPanel.setVisibility(View.GONE);
                }
                resetHideControlTimer();
            }
        });

        View.OnClickListener speedClickListener = new View.OnClickListener() {
            @Override public void onClick(View v) {
                int id = v.getId();
                if (id == R.id.tvSpeed08) currentSpeed = 0.8f;
                else if (id == R.id.tvSpeed10) currentSpeed = 1.0f;
                else if (id == R.id.tvSpeed125) currentSpeed = 1.25f;
                else if (id == R.id.tvSpeed15) currentSpeed = 1.5f;
                else if (id == R.id.tvSpeed20) currentSpeed = 2.0f;
                setPlaybackSpeed(currentSpeed); 
                updateSpeedUI(); 
                llSpeedPanel.setVisibility(View.GONE); 
                resetHideControlTimer();
            }
        };
        tvSpeed08.setOnClickListener(speedClickListener);
        tvSpeed10.setOnClickListener(speedClickListener);
        tvSpeed125.setOnClickListener(speedClickListener);
        tvSpeed15.setOnClickListener(speedClickListener);
        tvSpeed20.setOnClickListener(speedClickListener);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { 
                if (fromUser && mediaPlayer != null && isPrepared) mediaPlayer.seekTo(progress); 
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) { resetHideControlTimer(); }
        });

        findViewById(R.id.rootLayout).setOnTouchListener(this);
        resetHideControlTimer();
    }

    private void updateSpeedUI() {
        int defaultColor = 0xFFFFFFFF; int selectedColor = 0xFF2185F5;
        tvSpeed08.setTextColor(currentSpeed == 0.8f ? selectedColor : defaultColor);
        tvSpeed10.setTextColor(currentSpeed == 1.0f ? selectedColor : defaultColor);
        tvSpeed125.setTextColor(currentSpeed == 1.25f ? selectedColor : defaultColor);
        tvSpeed15.setTextColor(currentSpeed == 1.5f ? selectedColor : defaultColor);
        tvSpeed20.setTextColor(currentSpeed == 2.0f ? selectedColor : defaultColor);
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { 
        if (mediaPlayer == null) playVideo(currentVideoIndex, savePosition); 
    }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) { releaseMediaPlayer(); }

    @Override public void onConfigurationChanged(Configuration newConfig) { 
        super.onConfigurationChanged(newConfig); 
        adjustSurfaceViewSize(); 
    }

    // 使用物理屏幕全尺寸计算，配合 LAYOUT_STABLE，彻底消除视频重绘/跳动
    private void adjustSurfaceViewSize() {
        if (mediaPlayer != null && isPrepared) {
            int videoWidth = mediaPlayer.getVideoWidth(); 
            int videoHeight = mediaPlayer.getVideoHeight();
            if (videoWidth > 0 && videoHeight > 0) {
                DisplayMetrics dm = new DisplayMetrics(); 
                getWindowManager().getDefaultDisplay().getRealMetrics(dm);
                int screenWidth = dm.widthPixels;
                int screenHeight = dm.heightPixels;
                
                float scale = Math.min((float) screenWidth / videoWidth, (float) screenHeight / videoHeight);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (videoWidth * scale), (int) (videoHeight * scale));
                lp.gravity = Gravity.CENTER; 
                surfaceView.setLayoutParams(lp);
            }
        }
    }

    private void playVideo(int index, int seekTo) {
        if (videoList.isEmpty() || index < 0 || index >= videoList.size()) return;
        currentVideoIndex = index; String path = videoList.get(index);
        tvVideoTitle.setText(path.substring(path.lastIndexOf("/") + 1));

        releaseMediaPlayer(); mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC); mediaPlayer.setDisplay(surfaceHolder);

        try {
            mediaPlayer.setDataSource(this, Uri.parse(videoList.get(index)));
            mediaPlayer.setOnPreparedListener(this); mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.prepareAsync();
        } catch (Exception e) { e.printStackTrace(); Toast.makeText(this, "视频加载失败", Toast.LENGTH_SHORT).show(); }
        savePosition = seekTo; isPrepared = false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        isPrepared = true; mediaPlayer = mp;
        int duration = mediaPlayer.getDuration(); seekBar.setMax(duration); tvDuration.setText(formatTime(duration));

        if (savePosition > 0) mediaPlayer.seekTo(savePosition);
        setPlaybackSpeed(currentSpeed); updateSpeedUI(); adjustSurfaceViewSize();

        if (wasPlayingBeforePause) { 
            mediaPlayer.start(); 
            btnPlayPause.setImageResource(R.drawable.zantin); 
        } else { 
            btnPlayPause.setImageResource(R.drawable.bofang); 
        }
        handler.post(updateProgressRunnable);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        btnPlayPause.setImageResource(R.drawable.bofang); 
        wasPlayingBeforePause = false;
        Toast.makeText(this, "视频播放完毕", Toast.LENGTH_SHORT).show();
    }

    private void playNext() {
        if (currentVideoIndex < videoList.size() - 1) { currentSpeed = 1.0f; playVideo(currentVideoIndex + 1, 0); }
        else Toast.makeText(this, "已经是最后一个视频了", Toast.LENGTH_SHORT).show();
    }
    private void playPrev() {
        if (currentVideoIndex > 0) { currentSpeed = 1.0f; playVideo(currentVideoIndex - 1, 0); }
        else Toast.makeText(this, "已经是第一个视频了", Toast.LENGTH_SHORT).show();
    }

    private void setPlaybackSpeed(float speed) {
        if (mediaPlayer == null || !isPrepared) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try { mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed)); } catch (Exception e) { e.printStackTrace(); }
        } else Toast.makeText(this, "系统版本过低，不支持倍速", Toast.LENGTH_SHORT).show();
    }

    private final Runnable longPressRunnable = new Runnable() {
        @Override public void run() {
            if (!mediaPlayer.isPlaying()) return;
            isLongPress = true; 
            setPlaybackSpeed(2.5f);
            tvGestureType.setText("播放速度"); tvGestureValue.setText("2.5x");
            gestureProgressBar.setVisibility(View.GONE); gestureOverlay.setVisibility(View.VISIBLE);
        }
    };

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!isPrepared) return true;
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        Window window = getWindow(); WindowManager.LayoutParams lp = window.getAttributes();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mStartX = event.getX(); mStartY = event.getY();
                mStartVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                mStartBrightness = lp.screenBrightness < 0 ? 0.5f : lp.screenBrightness;
                mStartPosition = mediaPlayer.getCurrentPosition();
                isLeftSideBrightness = mStartX < screenWidth / 2f;
                gestureMode = 0; isMoved = false; isLongPress = false;
                gestureProgressBar.setVisibility(View.VISIBLE); 
                handler.postDelayed(longPressRunnable, LONG_PRESS_TIME);
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - mStartX; float dy = event.getY() - mStartY;
                if (!isMoved && (Math.abs(dx) > TOUCH_THRESHOLD || Math.abs(dy) > TOUCH_THRESHOLD)) {
                    isMoved = true; handler.removeCallbacks(longPressRunnable);
                    gestureMode = (Math.abs(dx) > Math.abs(dy)) ? 1 : 2;
                }
                if (gestureMode == 1) {
                    int duration = mediaPlayer.getDuration();
                    int newPos = Math.max(0, Math.min(mStartPosition + (int) (dx / (float) screenWidth * duration), duration));
                    mediaPlayer.seekTo(newPos); seekBar.setProgress(newPos);
                    tvGestureType.setText("进度"); tvGestureValue.setText(formatTime(newPos) + " / " + formatTime(duration));
                    gestureProgressBar.setMax(duration); gestureProgressBar.setProgress(newPos);
                    gestureOverlay.setVisibility(View.VISIBLE); return true;
                } else if (gestureMode == 2) {
                    if (isLeftSideBrightness) {
                        float newBr = Math.max(0.01f, Math.min(1.0f, mStartBrightness - dy * BRIGHT_SENS));
                        lp.screenBrightness = newBr; window.setAttributes(lp);
                        int percent = (int) (newBr * 100);
                        tvGestureType.setText("亮度"); tvGestureValue.setText(percent + "%");
                        gestureProgressBar.setMax(100); gestureProgressBar.setProgress(percent);
                    } else {
                        int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        int newVol = Math.max(0, Math.min(maxVol, mStartVolume + (int) (-dy * VOLUME_SENS)));
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0);
                        int percent = (maxVol > 0) ? (newVol * 100 / maxVol) : 0;
                        tvGestureType.setText("音量"); tvGestureValue.setText(percent + "%");
                        gestureProgressBar.setMax(100); gestureProgressBar.setProgress(percent);
                    }
                    gestureOverlay.setVisibility(View.VISIBLE); return true;
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressRunnable);
                if (isLongPress) {
                    isLongPress = false; setPlaybackSpeed(currentSpeed); updateSpeedUI(); gestureOverlay.setVisibility(View.GONE);
                } else if (!isMoved) {
                    long secondClickTime = System.currentTimeMillis();
                    if (secondClickTime - firstClickTime < 300) {
                        firstClickTime = 0; 
                        if (mediaPlayer != null && isPrepared) {
                            if (mediaPlayer.isPlaying()) {
                                mediaPlayer.pause(); 
                                btnPlayPause.setImageResource(R.drawable.bofang); 
                                wasPlayingBeforePause = false;
                            } else {
                                if (mediaPlayer.getCurrentPosition() >= mediaPlayer.getDuration() - 500) {
                                    mediaPlayer.seekTo(0); seekBar.setProgress(0); tvCurrent.setText("00:00");
                                }
                                mediaPlayer.start(); 
                                btnPlayPause.setImageResource(R.drawable.zantin); 
                                wasPlayingBeforePause = true;
                            }
                        }
                    } else {
                        firstClickTime = secondClickTime;
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (firstClickTime != 0) {
                                    toggleControlBar(); 
                                    firstClickTime = 0; 
                                }
                            }
                        }, 300);
                    }
                } else { 
                    gestureOverlay.setVisibility(View.GONE); 
                    resetHideControlTimer(); 
                }
                gestureMode = 0; isMoved = false; return true;
        }
        return true;
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.setOnPreparedListener(null); mediaPlayer.setOnCompletionListener(null);
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.release(); mediaPlayer = null; isPrepared = false;
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && isPrepared) {
            savePosition = mediaPlayer.getCurrentPosition();
            wasPlayingBeforePause = mediaPlayer.isPlaying();
            if (wasPlayingBeforePause) mediaPlayer.pause();
        }
        handler.removeCallbacks(updateProgressRunnable); handler.removeCallbacks(longPressRunnable);
    }

    @Override protected void onResume() {
        super.onResume();
        if (mediaPlayer != null && isPrepared && wasPlayingBeforePause) {
            mediaPlayer.seekTo(savePosition); 
            mediaPlayer.start(); 
            btnPlayPause.setImageResource(R.drawable.zantin);
        }
        handler.post(updateProgressRunnable); 
        resetHideControlTimer();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mediaPlayer != null && isPrepared) {
            outState.putInt("pos", mediaPlayer.getCurrentPosition());
            outState.putBoolean("was_playing", wasPlayingBeforePause);
            outState.putInt("video_index", currentVideoIndex);
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateProgressRunnable); handler.removeCallbacks(longPressRunnable);
        releaseMediaPlayer();
    }

    // 切换控制栏（此时不再需要同步状态栏，因为状态栏已强制隐藏）
    private void toggleControlBar() {
        if (isControlShow) {
            controlPanel.setVisibility(View.GONE);
            topPanel.setVisibility(View.GONE);
            isControlShow = false;
            handler.removeCallbacks(hideControlRunnable);
        } else {
            controlPanel.setVisibility(View.VISIBLE);
            topPanel.setVisibility(View.VISIBLE);
            isControlShow = true;
            resetHideControlTimer();
        }
    }

    private void resetHideControlTimer() {
        handler.removeCallbacks(hideControlRunnable);
        if (isControlShow) handler.postDelayed(hideControlRunnable, 4000);
    }

    // 自动隐藏控制栏
    private final Runnable hideControlRunnable = new Runnable() {
        @Override public void run() {
            if (llSpeedPanel.getVisibility() == View.VISIBLE) {
                handler.postDelayed(this, 2000);
            } else if (isControlShow) { 
                controlPanel.setVisibility(View.GONE); 
                topPanel.setVisibility(View.GONE); 
                isControlShow = false; 
            }
        }
    };

    private final Runnable updateProgressRunnable = new Runnable() {
        @Override public void run() {
            if (mediaPlayer != null && isPrepared && mediaPlayer.isPlaying()) {
                int current = mediaPlayer.getCurrentPosition();
                seekBar.setProgress(current); tvCurrent.setText(formatTime(current));
            }
            handler.postDelayed(this, 500);
        }
    };

    private String formatTime(int ms) {
        int s = ms / 1000; int m = s / 60; s = s % 60;
        return String.format("%02d:%02d", m, s);
    }
}
