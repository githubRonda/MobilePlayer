package com.ronda.mobileplayer.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ronda.mobileplayer.R;
import com.ronda.mobileplayer.domain.MediaItem;
import com.ronda.mobileplayer.utils.LogUtil;
import com.ronda.mobileplayer.utils.Utils;
import com.ronda.mobileplayer.view.VitamioVideoView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import io.vov.vitamio.MediaPlayer;
import io.vov.vitamio.Vitamio;

/**
 * 万能播放器界面
 * 和SystemVideoPlayer几乎是一模一样的,  其中的VideoView和MediaPlayer都换成了vitamio包中的类了
 *
 * 那为什么先用系统的播发器,播放出错的时候才使用万能播放器呢?
 * 原因在于系统播放器的效率要比第三方的要高. 万能播放器虽然各种格式的视频都可以播放, 但是同样开销也很大
 *
 */
public class VitamioVideoPlayer extends Activity implements View.OnClickListener {


    private boolean isUseSystem = true;// 监听卡的效果是使用系统封装的API(4.2版本才有)还是自定义的
    /**
     * 视频进度的更新
     */
    private static final int MSG_PROGRESS = 1;
    /**
     * 隐藏控制面板
     */
    private static final int MSG_HIDE_MEDIACONTROLLER = 2;


    /**
     * 显示网络速度
     */
    private static final int MSG_SHOW_SPEED = 3;
    /**
     * 全屏
     */
    private static final int FULL_SCREEN = 1;
    /**
     * 默认屏幕
     */
    private static final int DEFAULT_SCREEN = 2;
    private VitamioVideoView videoview;
    private Uri uri;
    private LinearLayout llTop;
    private TextView tvName;
    private ImageView ivBattery;
    private TextView tvSystemTime;
    private Button btnVoice;
    private SeekBar seekbarVoice;
    private Button btnSwichPlayer;
    private LinearLayout llBottom;
    private RelativeLayout media_controller;
    private TextView tvCurrentTime;
    private SeekBar seekbarVideo;
    private TextView tvDuration;
    private Button btnExit;
    private Button btnVideoPre;
    private Button btnVideoStartPause;
    private Button btnVideoNext;
    private Button btnVideoSiwchScreen;
    private TextView tv_buffer_netspeed;
    private LinearLayout ll_buffer;
    private TextView tv_laoding_netspeed;
    private LinearLayout ll_loading;

    private Utils utils;
    /**
     * 监听电量变化的广播
     */
    private MyReceiver receiver;
    /**
     * 传入进来的视频列表
     */
    private ArrayList<MediaItem> mediaItems;
    /**
     * 要播放的列表中的具体位置
     */
    private int position;

    /**
     * 1.定义手势识别器
     */
    private GestureDetector detector;

    /**
     * 是否显示控制面板
     */
    private boolean isshowMediaController = false;
    /**
     * 是否全屏
     */
    private boolean isFullScreen = false;

    /**
     * 屏幕的宽
     */
    private int screenWidth = 0;

    /**
     * 屏幕的高
     */
    private int screenHeight = 0;

    /**
     * 真实视频的宽
     */
    private int videoWidth;
    /**
     * 真实视频的高
     */
    private int videoHeight;

    /**
     * 调用声音
     */
    private AudioManager am;

    /**
     * 当前的音量
     */
    private int currentVoice;

    /**
     * 0~15
     * 最大音量
     */
    private int maxVoice;
    /**
     * 是否是静音
     */
    private boolean isMute = false;
    /**
     * 是否是网络uri
     */
    private boolean isNetUri;

    /**
     * 上一次的播放进度
     */
    private int precurrentPosition;


    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_SHOW_SPEED://显示网速
                    //1.得到网络速度
                    String netSpeed = utils.getNetSpeed(VitamioVideoPlayer.this);

                    //显示网络速
                    tv_laoding_netspeed.setText("玩命加载中..." + netSpeed);
                    tv_buffer_netspeed.setText("缓存中..." + netSpeed);

                    //2.每两秒更新一次
                    handler.removeMessages(MSG_SHOW_SPEED);
                    handler.sendEmptyMessageDelayed(MSG_SHOW_SPEED, 2000);

                    break;
                case MSG_HIDE_MEDIACONTROLLER://隐藏控制面板
                    hideMediaController();
                    break;
                case MSG_PROGRESS: // OnPreparedListener 触发之后, 每间隔1s更新一下播放进度

                    //1.得到当前的视频播放进程
                    int currentPosition = (int) videoview.getCurrentPosition();//0

                    //2.SeekBar.setProgress(当前进度);
                    seekbarVideo.setProgress(currentPosition);

                    //更新文本播放进度
                    tvCurrentTime.setText(utils.stringForTime(currentPosition));

                    //设置系统时间
                    tvSystemTime.setText(getSysteTime());

                    //缓存进度的更新
                    if (isNetUri) {
                        //只有网络资源才有缓存效果, 缓冲的计算算法如下
                        int buffer = videoview.getBufferPercentage();//0~100, 从VideoView中获取缓冲
                        int totalBuffer = buffer * seekbarVideo.getMax();
                        int secondaryProgress = totalBuffer / 100;
                        seekbarVideo.setSecondaryProgress(secondaryProgress);
                    } else {
                        //本地视频没有缓冲效果
                        seekbarVideo.setSecondaryProgress(0);
                    }

                    // 自定义监听视频卡顿效果(每秒执行一下)
                    // 原理:校验播放进度判断是否监听卡. 当前播放进度 - 上一次播放进度 理论值应该等于1s, 若过小的话, 说明卡顿
                    // m3u8 直播的视频流. 本质上是把视频分成了一小段一小段的, 所以每次获取currentPosition都为0, 自定义的这种实现监听卡顿效果就不再适用了, 只能换成使用api17之后系统自带的监听器实现了
                    if (!isUseSystem) {

                        if (videoview.isPlaying()) {
                            int buffer = currentPosition - precurrentPosition;
                            if (buffer < 500) {
                                //视频卡了
                                ll_buffer.setVisibility(View.VISIBLE);
                            } else {
                                //视频不卡了
                                ll_buffer.setVisibility(View.GONE);
                            }
                        } else {
                            ll_buffer.setVisibility(View.GONE);
                        }
                    }

                    precurrentPosition = currentPosition;

                    //3.每秒更新一次
                    handler.removeMessages(MSG_PROGRESS);
                    handler.sendEmptyMessageDelayed(MSG_PROGRESS, 1000);

                    break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);//初始化父类
        LogUtil.e("onCreate--");

        Vitamio.isInitialized(this); // 初始化Vitamio库，在布局文件加载之前

        setContentView(R.layout.activity_vitamio_video_player);

        initData();

        findViews();

        setListener();

        getIntentData();


        //设置控制面板. 在布局文件中使用自定义的控制面板布局
//        videoview.setMediaController(new MediaController(this));
    }

    private void initData() {
        utils = new Utils();
        //注册电量广播
        receiver = new MyReceiver();
        IntentFilter intentFiler = new IntentFilter();
        //当电量变化的时候发这个广播
        intentFiler.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(receiver, intentFiler);

        //2.实例化手势识别器，并且重写双击，点击，长按
        detector = new GestureDetector(this, new MySimpleOnGestureListener());


        //得到屏幕的宽和高(已过时)
//        screenWidth = getWindowManager().getDefaultDisplay().getWidth();
//        screenHeight = getWindowManager().getDefaultDisplay().getHeight();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        screenWidth = displayMetrics.widthPixels;
        screenHeight = displayMetrics.heightPixels;

        // 使用AudioManager获取当前音量值及其范围
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        currentVoice = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        maxVoice = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

    }

    /**
     * Auto-created on 2016-07-18 15:26:59 by Android Layout Finder
     * (http://www.buzzingandroid.com/tools/android-layout-finder)
     */
    private void findViews() {

        llTop = (LinearLayout) findViewById(R.id.ll_top);
        tvName = (TextView) findViewById(R.id.tv_name);
        ivBattery = (ImageView) findViewById(R.id.iv_battery);
        tvSystemTime = (TextView) findViewById(R.id.tv_system_time);
        btnVoice = (Button) findViewById(R.id.btn_voice);
        seekbarVoice = (SeekBar) findViewById(R.id.seekbar_voice);
        btnSwichPlayer = (Button) findViewById(R.id.btn_swich_player);
        llBottom = (LinearLayout) findViewById(R.id.ll_bottom);
        tvCurrentTime = (TextView) findViewById(R.id.tv_current_time);
        seekbarVideo = (SeekBar) findViewById(R.id.seekbar_video);
        tvDuration = (TextView) findViewById(R.id.tv_duration);
        btnExit = (Button) findViewById(R.id.btn_exit);
        btnVideoPre = (Button) findViewById(R.id.btn_video_pre);
        btnVideoStartPause = (Button) findViewById(R.id.btn_video_start_pause);
        btnVideoNext = (Button) findViewById(R.id.btn_video_next);
        btnVideoSiwchScreen = (Button) findViewById(R.id.btn_video_siwch_screen);
        videoview = (VitamioVideoView) findViewById(R.id.videoview);
        media_controller = (RelativeLayout) findViewById(R.id.media_controller);
        tv_buffer_netspeed = (TextView) findViewById(R.id.tv_buffer_netspeed);
        ll_buffer = (LinearLayout) findViewById(R.id.ll_buffer);
        tv_laoding_netspeed = (TextView) findViewById(R.id.tv_laoding_netspeed);
        ll_loading = (LinearLayout) findViewById(R.id.ll_loading);

        btnVoice.setOnClickListener(this);
        btnSwichPlayer.setOnClickListener(this);
        btnExit.setOnClickListener(this);
        btnVideoPre.setOnClickListener(this);
        btnVideoStartPause.setOnClickListener(this);
        btnVideoNext.setOnClickListener(this);
        btnVideoSiwchScreen.setOnClickListener(this);

        //最大音量和SeekBar关联
        seekbarVoice.setMax(maxVoice);
        //设置当前进度-当前音量
        seekbarVoice.setProgress(currentVoice);


        //开始更新网络速度
        handler.sendEmptyMessage(MSG_SHOW_SPEED);

    }


    @Override
    public void onClick(View v) {
        if (v == btnVoice) {
            isMute = !isMute;
            // Handle clicks for btnVoice
            updataVoice(currentVoice, isMute);
        } else if (v == btnSwichPlayer) {
            // Handle clicks for btnSwichPlayer
            showSwichPlayerDialog();
        } else if (v == btnExit) {
            // Handle clicks for btnExit
            finish();
        } else if (v == btnVideoPre) {
            // Handle clicks for btnVideoPre
            playPreVideo();
        } else if (v == btnVideoStartPause) {
            // Handle clicks for btnVideoStartPause
            startAndPause();
        } else if (v == btnVideoNext) {
            // Handle clicks for btnVideoNext
            playNextVideo();
        } else if (v == btnVideoSiwchScreen) {
            // Handle clicks for btnVideoSiwchScreen
            setFullScreenAndDefault();
        }

        handler.removeMessages(MSG_HIDE_MEDIACONTROLLER);
        handler.sendEmptyMessageDelayed(MSG_HIDE_MEDIACONTROLLER, 4000);
    }


    private void setFullScreenAndDefault() {
        if (isFullScreen) {
            //默认
            setVideoType(DEFAULT_SCREEN);
        } else {
            //全屏
            setVideoType(FULL_SCREEN);
        }
    }


    private void showSwichPlayerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("万能播放器提醒您");
        builder.setMessage("当您播放一个视频，有花屏的话，可以尝试使用系统播放器播放");
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startSystemPlayer();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void startSystemPlayer() {
        if(videoview != null){
            videoview.stopPlayback();
        }


        Intent intent = new Intent(this,SystemVideoPlayer.class);
        if(mediaItems != null && mediaItems.size() > 0){

            Bundle bundle = new Bundle();
            bundle.putSerializable("videolist", mediaItems);
            intent.putExtras(bundle);
            intent.putExtra("position", position);

        }else if(uri != null){
            intent.setData(uri);
        }
        startActivity(intent);

        finish();//关闭页面
    }

    private void startAndPause() {
        if (videoview.isPlaying()) {
            //视频在播放-设置暂停
            videoview.pause();
            //按钮状态设置播放
            btnVideoStartPause.setBackgroundResource(R.drawable.btn_video_start_selector);
        } else {
            //视频播放
            videoview.start();
            //按钮状态设置暂停
            btnVideoStartPause.setBackgroundResource(R.drawable.btn_video_pause_selector);
        }
    }

    /**
     * 播放上一个视频
     */
    private void playPreVideo() {
        if (mediaItems != null && mediaItems.size() > 0) {
            //播放上一个视频
            position--;
            if (position >= 0) {
                ll_loading.setVisibility(View.VISIBLE);
                MediaItem mediaItem = mediaItems.get(position);
                tvName.setText(mediaItem.getName());
                isNetUri = utils.isNetUri(mediaItem.getData());
                videoview.setVideoPath(mediaItem.getData());

                //设置按钮状态
                setButtonState();

                // 切换到下一个视频时, 会立即处于播放状态, 无论前一刻是否暂停, 所以此刻要更新 开始/暂停 按钮
                btnVideoStartPause.setBackgroundResource(R.drawable.btn_video_pause_selector);
            }
        } else if (uri != null) {
            //设置按钮状态-上一个和下一个按钮设置灰色并且不可以点击
            setButtonState();
        }
    }

    /**
     * 播放下一个视频
     */
    private void playNextVideo() {
        if (mediaItems != null && mediaItems.size() > 0) {
            //播放下一个
            position++;
            if (position < mediaItems.size()) {

                ll_loading.setVisibility(View.VISIBLE);
                MediaItem mediaItem = mediaItems.get(position);
                tvName.setText(mediaItem.getName());
                isNetUri = utils.isNetUri(mediaItem.getData());
                videoview.setVideoPath(mediaItem.getData());

                //设置按钮状态
                setButtonState();

                // 切换到下一个视频时, 会立即处于播放状态, 无论前一刻是否暂停, 所以此刻要更新 开始/暂停 按钮
                btnVideoStartPause.setBackgroundResource(R.drawable.btn_video_pause_selector);

            }
        } else if (uri != null) {
            //设置按钮状态-上一个和下一个按钮设置灰色并且不可以点击
            setButtonState();
        }

    }

    private void setButtonState() {

        if (mediaItems != null && mediaItems.size() > 0) {
            // todo 直接判断position和size的关系, 确定prev和next按钮是否可用
            // 设置prev按钮状态
            if (position == 0) {
                btnVideoPre.setEnabled(false);
            } else {
                btnVideoPre.setEnabled(true);
            }

            // 设置next按钮状态
            if (position == mediaItems.size() - 1) {
                btnVideoNext.setEnabled(false);
            } else {
                btnVideoNext.setEnabled(true);
            }
        } else if (uri != null) {
            //两个按钮设置灰色
            setEnable(false);
        }
    }

    private void setEnable(boolean isEnable) {
        if (isEnable) {
            btnVideoPre.setBackgroundResource(R.drawable.btn_video_pre_selector);
            btnVideoPre.setEnabled(true);
            btnVideoNext.setBackgroundResource(R.drawable.btn_video_next_selector);
            btnVideoNext.setEnabled(true);
        } else {
            //两个按钮设置灰色
            btnVideoPre.setBackgroundResource(R.drawable.btn_pre_gray);
            btnVideoPre.setEnabled(false);
            btnVideoNext.setBackgroundResource(R.drawable.btn_next_gray);
            btnVideoNext.setEnabled(false);
        }

    }


    /**
     * 得到系统时间
     *
     * @return
     */
    public String getSysteTime() {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
        return format.format(new Date());
    }


    private void getIntentData() {
        uri = getIntent().getData();//播放网络资源, 或外部程序调用该播放器的时候该uri才不为null
        //得到播放列表, 以及当前位置
        mediaItems = (ArrayList<MediaItem>) getIntent().getSerializableExtra("videolist");
        position = getIntent().getIntExtra("position", 0);


        if (mediaItems != null && mediaItems.size() > 0) {
            MediaItem mediaItem = mediaItems.get(position);
            tvName.setText(mediaItem.getName());//设置视频的名称
            isNetUri = utils.isNetUri(mediaItem.getData());
            videoview.setVideoPath(mediaItem.getData());

        } else if (uri != null) {
            tvName.setText(uri.toString());//设置视频的名称
            isNetUri = utils.isNetUri(uri.toString());
            videoview.setVideoURI(uri);
        } else {
            Toast.makeText(VitamioVideoPlayer.this, "没有传递数据", Toast.LENGTH_SHORT).show();
        }
        setButtonState();

    }


    private void setVideoType(int defaultScreen) {
        switch (defaultScreen) {
            case FULL_SCREEN://全屏
                //1.设置视频画面的大小-屏幕有多大就是多大
                videoview.setVideoSize(screenWidth, screenHeight);
                //2.设置按钮的状态-默认
                btnVideoSiwchScreen.setBackgroundResource(R.drawable.btn_video_siwch_screen_default_selector);
                isFullScreen = true;
                break;
            case DEFAULT_SCREEN://默认
                //1.设置视频画面的大小
                //视频真实的宽和高
                int mVideoWidth = videoWidth;
                int mVideoHeight = videoHeight;

                //屏幕的宽和高
                int width = screenWidth;
                int height = screenHeight;


                // VideoView#onMeasure()中, 有如下计算视频大小的算法, 即把原始视频的宽高放大至与屏幕宽高其中一边相等. 是等比例缩放.
                // for compatibility, we adjust size based on aspect ratio
                if (mVideoWidth * height < width * mVideoHeight) {
                    //Log.i("@@@", "image too wide, correcting");
                    width = height * mVideoWidth / mVideoHeight;
                } else if (mVideoWidth * height > width * mVideoHeight) {
                    //Log.i("@@@", "image too tall, correcting");
                    height = width * mVideoHeight / mVideoWidth;
                }

                videoview.setVideoSize(width, height);
                //2.设置按钮的状态--全屏
                btnVideoSiwchScreen.setBackgroundResource(R.drawable.btn_video_siwch_screen_full_selector);
                isFullScreen = false;
                break;
        }
    }


    private void setBattery(int level) {
        if (level <= 0) {
            ivBattery.setImageResource(R.drawable.ic_battery_0);
        } else if (level <= 10) {
            ivBattery.setImageResource(R.drawable.ic_battery_10);
        } else if (level <= 20) {
            ivBattery.setImageResource(R.drawable.ic_battery_20);
        } else if (level <= 40) {
            ivBattery.setImageResource(R.drawable.ic_battery_40);
        } else if (level <= 60) {
            ivBattery.setImageResource(R.drawable.ic_battery_60);
        } else if (level <= 80) {
            ivBattery.setImageResource(R.drawable.ic_battery_80);
        } else if (level <= 100) {
            ivBattery.setImageResource(R.drawable.ic_battery_100);
        } else {
            ivBattery.setImageResource(R.drawable.ic_battery_100);
        }
    }

    private void setListener() {
        //准备好的监听
        videoview.setOnPreparedListener(new MyOnPreparedListener());

        //播放出错了的监听
        videoview.setOnErrorListener(new MyOnErrorListener());

        //播放完成了的监听
        videoview.setOnCompletionListener(new MyOnCompletionListener());

        //设置SeeKbar状态变化的监听
        seekbarVideo.setOnSeekBarChangeListener(new VideoOnSeekBarChangeListener());

        seekbarVoice.setOnSeekBarChangeListener(new VoiceOnSeekBarChangeListener());

        if (isUseSystem) {
            //监听视频播放卡-系统的api
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                videoview.setOnInfoListener(new MyOnInfoListener());
            }
        }
    }


    /**
     * 设置音量的大小
     * 调节音量是分两种情况的:
     * 1. 非静音的调节(静音撤销按钮 和 音量值在1~15直接调节)
     * 2. 静音的调节(静音按钮, 和 音量值调为0情况)
     *
     * @param progress
     */
    private void updataVoice(int progress, boolean isMute) {
        if (isMute) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);// 第三个参数是一个flag, 若为FLAG_SHOW_UI即值为1, 则调整时显示音量条. 为0表示不显示音量条
            seekbarVoice.setProgress(0);
        } else {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
            seekbarVoice.setProgress(progress);
            currentVoice = progress; // 记录非静音时的当前音量
        }
    }




    @Override
    protected void onDestroy() {

        //移除所有的消息
        handler.removeCallbacksAndMessages(null);


        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }
        LogUtil.e("onDestroy--");

        //释放资源的时候，先释放子类，在释放父类. 所以super写在下面
        super.onDestroy();
    }

    private float startY;
    private float startX;
    /**
     * 屏幕的高
     */
    private float touchRang;

    /**
     * 当一按下的音量
     */
    private int mVol;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //3.把事件传递给手势识别器
        /**
         * 1. 控制面板的隐藏与否:
         * -- 单击屏幕, 若显示则变为隐藏且把消息移除, 若隐藏则变为显示; 且变为显示之后, 还要延迟发送一个隐藏面板的消息
         * -- 手指按下, 移除隐藏消息; 手指抬起, 在延迟发送一个隐藏面板的消息. --> 当面板显示时, 若一直触摸屏幕(滑动进度或声音), 则面板始终显示
         */
        detector.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN://手指按下
                //1.按下记录值
                startY = event.getY();
                startX = event.getX();
                mVol = am.getStreamVolume(AudioManager.STREAM_MUSIC); // 当前音量
                touchRang = Math.min(screenHeight, screenWidth);//screenHeight
                handler.removeMessages(MSG_HIDE_MEDIACONTROLLER);

                break;
            case MotionEvent.ACTION_MOVE://手指移动
                //2.移动的记录相关值
                float endY = event.getY();
                float endX = event.getX();
                float distanceY = startY - endY;

                if (endX < screenWidth / 2) {
                    //左边屏幕-调节亮度
                    final double FLING_MIN_DISTANCE = 0.5;
                    final double FLING_MIN_VELOCITY = 0.5;
                    if (distanceY > FLING_MIN_DISTANCE && Math.abs(distanceY) > FLING_MIN_VELOCITY) {//up
                        setBrightness(20);
                    }
                    if (distanceY < FLING_MIN_DISTANCE && Math.abs(distanceY) > FLING_MIN_VELOCITY) {//down
                        setBrightness(-20);
                    }
                } else {
                    //右边屏幕-调节声音
                    //改变声音 = （滑动屏幕的距离： 总距离）*音量最大值
                    float delta = (distanceY / touchRang) * maxVoice;
                    //最终声音 = 原来的 + 改变声音；
                    int voice = (int) Math.min(Math.max(mVol + delta, 0), maxVoice); // 把计算的值限制在[0, 15]之间, 用max和min分别限制下
                    if (delta != 0) { // 说明要改变声音
                        if (voice > 0) {
                            isMute = false;
                        } else {
                            isMute = true;
                        }
                        updataVoice(voice, isMute);
                    }
                }
                break;
            case MotionEvent.ACTION_UP://手指离开
                handler.sendEmptyMessageDelayed(MSG_HIDE_MEDIACONTROLLER, 4000);
                break;
        }
        return super.onTouchEvent(event);
    }

    private Vibrator vibrator;

    /*
     *
     * 设置屏幕亮度 lp = 0 全暗 ，lp= -1,根据系统设置， lp = 1; 最亮
     */
    public void setBrightness(float brightness) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        // if (lp.screenBrightness <= 0.1) {
        // return;
        // }
        lp.screenBrightness = lp.screenBrightness + brightness / 255.0f;
        if (lp.screenBrightness > 1) {
            lp.screenBrightness = 1;
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            long[] pattern = {10, 200}; // OFF/ON/OFF/ON...
            vibrator.vibrate(pattern, -1);
        } else if (lp.screenBrightness < 0.2) {
            lp.screenBrightness = (float) 0.2;
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            long[] pattern = {10, 200}; // OFF/ON/OFF/ON...
            vibrator.vibrate(pattern, -1);
        }
//        Log.e(TAG, "lp.screenBrightness= " + lp.screenBrightness);
        getWindow().setAttributes(lp);
    }


    /**
     * 显示控制面板
     */
    private void showMediaController() {
        media_controller.setVisibility(View.VISIBLE);
        isshowMediaController = true;
    }


    /**
     * 隐藏控制面板
     */
    private void hideMediaController() {
        media_controller.setVisibility(View.GONE);
        isshowMediaController = false;
    }

    /**
     * 监听物理健，实现声音的调节大小, 使得控制声音的seekbar也随之更改
     *
     * @param keyCode
     * @param event
     * @return
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { // 按系统声音减键
            currentVoice--;
            updataVoice(currentVoice, false);
            handler.removeMessages(MSG_HIDE_MEDIACONTROLLER);
            handler.sendEmptyMessageDelayed(MSG_HIDE_MEDIACONTROLLER, 4000);
            return true; // 返回false的话, 会同时显示系统的声音调节的提示框
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {// 按系统声音加键
            currentVoice++;
            updataVoice(currentVoice, false);
            handler.removeMessages(MSG_HIDE_MEDIACONTROLLER);
            handler.sendEmptyMessageDelayed(MSG_HIDE_MEDIACONTROLLER, 4000);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    class VideoOnSeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {

        /**
         * 当手指滑动的时候，会引起SeekBar进度变化，会回调这个方法
         *
         * @param seekBar
         * @param progress
         * @param fromUser 如果是用户引起的true,不是用户引起的false
         */
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                videoview.seekTo(progress);
            }
        }

        /**
         * 当手指触碰的时候回调这个方法
         *
         * @param seekBar
         */
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

            handler.removeMessages(MSG_HIDE_MEDIACONTROLLER);
        }

        /**
         * 当手指离开的时候回调这个方法
         *
         * @param seekBar
         */
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            handler.sendEmptyMessageDelayed(MSG_HIDE_MEDIACONTROLLER, 4000);
        }
    }

    class MyOnPreparedListener implements MediaPlayer.OnPreparedListener {

        //当底层解码准备好的时候
        @Override
        public void onPrepared(MediaPlayer mp) {
            videoWidth = mp.getVideoWidth();
            videoHeight = mp.getVideoHeight();
            videoview.start();//开始播放
            //1.视频的总时长，关联总长度
            int duration = (int) videoview.getDuration();// mp.getDuration(); 也是可以的, 本质上都是调用mMediaPlayer.getDuration()
            seekbarVideo.setMax(duration);
            tvDuration.setText(utils.stringForTime(duration));

            hideMediaController();//默认是隐藏控制面板
            //2.发消息
            handler.sendEmptyMessage(MSG_PROGRESS);

            //屏幕的默认播放
            setVideoType(DEFAULT_SCREEN);

            //把加载页面消失掉
            ll_loading.setVisibility(View.GONE);

            // 拖动完成也有一个监听
//            mp.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
//                @Override
//                public void onSeekComplete(MediaPlayer mp) {
//                    Toast.makeText(SystemVideoPlayer.this, "拖动完成", Toast.LENGTH_SHORT).show();
//                }
//            });


        }
    }

    class MyOnErrorListener implements MediaPlayer.OnErrorListener {

        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
//            Toast.makeText(SystemVideoPlayer.this, "播放出错了哦", Toast.LENGTH_SHORT).show();
            // 播放出错的几种原因
            //1.播放的视频格式不支持--跳转到万能播放器继续播放
            //2.播放网络视频的时候，网络中断---1.如果网络确实断了，可以提示用于网络断了；2.网络断断续续的，则重新播放
            //3.播放的时候本地文件中间有空白---下载做完成

            // 万能播放器播放出错的时候, 弹出一个对话框
            // 若返回false的话, 系统会默认弹出一个对话框. 可以查看setOnErrorListener源码, 点击确定之后并不会finish当前界面
            // 所以我们自己弹出一个对话框, 播放出错的时候, 结束掉当前界面

            showErrorDialog();
            return true;
        }
    }

    private void showErrorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("提示");
        builder.setMessage("抱歉，无法播放该视频！！");
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        builder.show();
    }

    class MyOnCompletionListener implements MediaPlayer.OnCompletionListener {

        @Override
        public void onCompletion(MediaPlayer mp) {
            playNextVideo();
//            Toast.makeText(SystemVideoPlayer.this, "播放完成了="+uri, Toast.LENGTH_SHORT).show();
        }
    }

    class MyOnInfoListener implements MediaPlayer.OnInfoListener {

        @Override
        public boolean onInfo(MediaPlayer mp, int what, int extra) {
            switch (what) {
                case MediaPlayer.MEDIA_INFO_BUFFERING_START://视频卡了，拖动卡
                    ll_buffer.setVisibility(View.VISIBLE);
                    break;

                case MediaPlayer.MEDIA_INFO_BUFFERING_END://视频卡结束了，拖动卡结束了
                    ll_buffer.setVisibility(View.GONE);
                    break;
            }
            return true;
        }
    }

    class VoiceOnSeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                if (progress > 0) {
                    isMute = false;
                } else {
                    isMute = true;
                }
                updataVoice(progress, isMute);
            }

        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            handler.removeMessages(MSG_HIDE_MEDIACONTROLLER);

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            handler.sendEmptyMessageDelayed(MSG_HIDE_MEDIACONTROLLER, 4000);
        }
    }

    class MySimpleOnGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public void onLongPress(MotionEvent e) { // 长按
            super.onLongPress(e);
            startAndPause();
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) { // 双击 全屏显示与否
            setFullScreenAndDefault();
            return super.onDoubleTap(e);

        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) { // 单击
            if (isshowMediaController) {
                //隐藏
                hideMediaController();
                //把隐藏消息移除
                handler.removeMessages(MSG_HIDE_MEDIACONTROLLER);

            } else {
                //显示
                showMediaController();
                //发消息隐藏
                handler.sendEmptyMessageDelayed(MSG_HIDE_MEDIACONTROLLER, 4000);
            }

            return super.onSingleTapConfirmed(e);
        }
    }

    class MyReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra("level", 0);//范围: 0~100;
            setBattery(level);//主线程
        }
    }


}

