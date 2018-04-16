#启动页SplashActivity
注意点:

1. 即使多次调用 startMainActivity() 也只执行跳转一次. 添加flag
2. 启动时立即退出,避免2s后又自动跳转. 所以退出时要清空handler中的所有消息



#软件结构分析

* VideoPager
* AudioPager
* NetVideoPager
* NetAudioPager

上面四个页面继承BasePager, 然后实例化四个页面并且放入集合, 当点击RadioGruop的时候，监听RadioGruop状态变化，显示不同页面，即在MainActivity切换界面时, 每次都是创建一个Fragment,并且复写onCreateView方法,同时把对应的BasePager中的rootView返回

(感觉这里可以优化: MainActivity中可以再创建一个集合,装载Fragment. 这样就不用每次都创建Fragment了)




#本地视频列表

1.写布局
 相对布局 ： ListView和TextView和ProgressBar，初始化

2.加载本地的视频，在子线程中加载视频，加载的视频放入集合中；
  设置适配器要在主线程；用Handler,设置适配器

3.设置item的点击事件，把视频播放出来


# 播放视频的几种方式:

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

        MediaItem mediaItem = mediaItems.get(position);

        /*
        //1.调起系统所有的播放-隐式意图
        Intent intent = new Intent();
        intent.setDataAndType(Uri.parse(mediaItem.getData()), "video/*");
        context.startActivity(intent);
        */

		/*
        //2.调用自己写的播放器-显示意图--一个播放地址
		// SystemVideoPlayer 中使用getIntent().getData();获取URI, 然后使用videoview.setVideoURI(uri);设置URI; 
		// 还可以使用 videoview.setMediaController(new MediaController(this)); 添加一个控制区域
        Intent intent = new Intent(context, SystemVideoPlayer.class);
        intent.setDataAndType(Uri.parse(mediaItem.getData()), "video/*");
        context.startActivity(intent);
		*/

        //3.传递列表数据-对象-序列化
        Intent intent = new Intent(context, SystemVideoPlayer.class);
        Bundle bundle = new Bundle();
        bundle.putSerializable("videolist", mediaItems);
        intent.putExtras(bundle);
        intent.putExtra("position", position);
        context.startActivity(intent);
    }


#MediaPlayer和VideoView
Android 系统中提供开发者开发多媒体应用（音视频方面）

一，MediaPlayer，解码的是底层，MediaPlayer负责和底层打交道，封装了很多方法
 start,pause,stop ,播放视频的类

这个MediaPlayer可以播放本地 和网络 的音视频.

 注意: 播放网络资源的时候，要联网权限

1,执行流程(MediaPlayer流程图)
2.视频支持的格式
 mp4,3gp,.m3u8(网络视频)
 直接用pc的.mp4文件, 不一定可以播放, 因为pc端的mp4文件可能码率比较高(high级别), 而移动设备支持的码率级别是base

二，VideoView
 显示视频，继承SurfaceView类，实现MediaController.MediaPlayerControl接口，封装了MediaPlayer, 而且VideoView中的 start,pause,stop等方法,本质上是调用MediaPlayer的

	public class VideoView extends SurfaceView
	        implements MediaPlayerControl, SubtitleController.Anchor {

 
SurfaceView特点:

1. 内嵌Surface, 且Surface采用纵深排序, 总是位于窗口后面
2. 兄弟视图会在视图层级的顶端显示, 可以添加overlay层
3. 若有透明控件, 每次Surface变化都会重新计算透明效果, 影响性能
4. 默认使用双缓存技术, 且在子线程中绘制, 不会阻塞主线程


 MediaPlayerControl接口,是MediaController的内部接口，是对媒体播放常用功能的抽象

	// 可以发现 MediaController 就是一个自定义View, 在makeControllerView方法中加载控制按钮布局,initControllerView()中添加事件. 而且该布局有竖屏和横屏两个布局
	public class MediaController extends FrameLayout {
	
		...
	    protected View makeControllerView() {
	        LayoutInflater inflate = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	        mRoot = inflate.inflate(com.android.internal.R.layout.media_controller, null);
	
	        initControllerView(mRoot);
	
	        return mRoot;
	    }
	
		 public interface MediaPlayerControl {
		        void    start();
		        void    pause();
		        int     getDuration();
		        int     getCurrentPosition();
		        void    seekTo(int pos);
		        boolean isPlaying();
		        int     getBufferPercentage();
		        boolean canPause();
		        boolean canSeekBackward();
		        boolean canSeekForward();
		
		        /**
		         * Get the audio session id for the player used by this VideoView. This can be used to
		         * apply audio effects to the audio track of a video.
		         * @return The audio session, or 0 if there was an error.
		         */
		        int     getAudioSessionId();
		    }
	
	}


 videoview.setMediaController(new MediaController(context)); // 这里内部就是把videoView对象传入了到MediaController

	public class VideoView extends SurfaceView
	        implements MediaPlayerControl, SubtitleController.Anchor {
		...
	
	    public void setMediaController(MediaController controller) {
	        if (mMediaController != null) {
	            mMediaController.hide();
	        }
	        mMediaController = controller;
	        attachMediaController();
	    }
	
	    private void attachMediaController() {
	        if (mMediaPlayer != null && mMediaController != null) {
	            mMediaController.setMediaPlayer(this);  // 把videoView对象传入了到MediaController中, 且videoView还实现了MediaController.MediaPlayerControl接口
	            View anchorView = this.getParent() instanceof View ?
	                    (View)this.getParent() : this;
	            mMediaController.setAnchorView(anchorView); //以VideoView的父级标View为锚点, 对MediaController定位
	            mMediaController.setEnabled(isInPlaybackState());
	        }
	    }
		....
	}


#Activity的生命周期和横竖屏切换的生命周期

一，生命周期

1.创建Activity的时候执行的方法
onCreate-->onStart-->onResume

2.销毁Activity的时候执行的方法
onPause-->onStop->onDestroy


二，A页面跳转到B页面，点击返回，这个过程中的生命周期


B页面完全覆盖A页面的情况
A跳转B页面的生命周期方法执行顺序：
onPause(A)->onCreate(B)--->onStart(B)--->onResume(B)--->onStop(A)

B页面点击后返回生命周期执行顺序：
onPause(B)-->onRestart(A)-->onStart(A)-->onResume(A)->onStop(B)-->onDestroy(B)



B页面不完全覆盖A页面的请求
A跳转B页面的生命周期方法执行顺序
onPause(A)-->onCreate(B)-->onStart(B)-->onResume(B)
B页面点击后返回生命周期执行顺序
onPause(B)-->onResume(A)-->->onStop(B)-->onDestroy(B)



#Activity横竖屏切换的生命周期
默认情况：onPause-->onStop-->onDestroy-->onCreate-->onStart-->onResume

屏幕横竖屏切换导致生命周期重新执行

	<activity android:name=".activity.SystemVideoPlayer"
	    android:configChanges="keyboardHidden|screenSize|orientation"
	    />


# 根据布局自动生成代码实例的网站
	https://www.buzzingandroid.com/tools/android-layout-finder/



#系统播放界面功能

1. 控制面板的延迟隐藏     handler.sendEmptyMessageDelayed()
-- 单击屏幕, 若显示则变为隐藏且把消息移除, 若隐藏则变为显示; 且变为显示之后, 还要延迟发送一个隐藏面板的消息
-- 手指按下, 移除隐藏消息; 手指抬起, 在延迟发送一个隐藏面板的消息. --> 当面板显示时, 若一直触摸屏幕(滑动进度或声音), 则面板始终显示
<p>
2. 系统时间的实时获取与更新 (每秒更新一次)
<p>
3. 电量的更新  (动态注册广播BATTERY_CHANGED: int level = intent.getIntExtra("level", 0);//范围: 0~100;)
<p>
4. 暂停/播放功能. videoview.pause(); / videoview.start();
<p>
5. 播放进度的实时更新与调节功能  (每秒更新一次, videoview.seekTo(progress);)
<p>
6. 上/下个视频切换功能 videoview.setVideoPath(mediaItem.getData()); 设置上/下个视频的切换路径
<p>
7. 全屏与否(点击按钮和双击屏幕[手势识别器]). 自定义 VideoView 控件, 拓展一个功能, 从而改变VideoView控件测量时的大小
<p>
8. 调节音量与静音: 滑块调节和触屏调节. 借助 AudioManager. 且还要监听物理按键改变声音
--  int voice = (int) Math.min(Math.max(mVol + delta, 0), maxVoice); // 把计算的值限制在[0, 15]之间, 用max和min分别限制下
<p>
9. 调节亮度



# 播放界面的过滤器配置

    <intent-filter>
        <action android:name="android.intent.action.VIEW" />

        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data android:scheme="rtsp" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />

        <data android:mimeType="video/*" />
        <data android:mimeType="application/sdp" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />

        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data android:scheme="http" />
        <data android:mimeType="video/mp4" />
        <data android:mimeType="video/3gp" />
        <data android:mimeType="video/3gpp" />
        <data android:mimeType="video/3gpp2" />
    </intent-filter>

    <intent-filter>
        <action android:name="android.intent.action.VIEW" />

        <category android:name="android.intent.category.LAUNCHER" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data
            android:mimeType="video/*"
            android:scheme="http" />
        <data
            android:mimeType="video/*"
            android:scheme="rtsp" />
        <data
            android:mimeType="video/*"
            android:scheme="rtmp" />
        <data
            android:mimeType="video/*"
            android:scheme="udp" />
        <data
            android:mimeType="video/*"
            android:scheme="tcp" />
        <data
            android:mimeType="video/*"
            android:scheme="file" />
        <data
            android:mimeType="video/*"
            android:scheme="content" />
        <data
            android:mimeType="video/*"
            android:scheme="mms" />
        <data android:mimeType="application/octet-stream" />
        <data android:mimeType="application/x-mpegurl" />
        <data android:mimeType="application/vnd.apple.mpegurl" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />

        <category android:name="android.intent.category.DEFAULT" />

        <data android:scheme="content" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />

        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data
            android:mimeType="application/x-mpegurl"
            android:scheme="http" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />

        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data android:scheme="rtsp" />
        <data android:scheme="rtmp" />
        <data android:scheme="mms" />
        <data android:scheme="tcp" />
        <data android:scheme="udp" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <action android:name="android.intent.action.SEND" />
        <action android:name="android.intent.action.SENDTO" />

        <category android:name="android.intent.category.DEFAULT" />

        <data android:mimeType="video/*" />
        <data android:mimeType="application/sdp" />
        <data android:mimeType="application/octet-stream" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />

        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data android:scheme="http" />
        <data android:mimeType="video/*" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />

        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data android:scheme="file" />
        <data android:scheme="content" />
        <data android:scheme="http" />
        <data android:scheme="https" />
        <data android:scheme="ftp" />
        <data android:scheme="rtsp" />
        <data android:scheme="rtmp" />
        <data android:scheme="mms" />
        <data android:scheme="tcp" />
        <data android:scheme="udp" />
        <data android:scheme="gopher" />
        <data android:mimeType="video/*" />
        <!-- <data android:mimeType="audio/*" /> -->
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />

        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data android:scheme="file" />
        <data android:scheme="content" />
        <data android:scheme="http" />
        <data android:scheme="https" />
        <data android:scheme="ftp" />
        <data android:scheme="rtsp" />
        <data android:scheme="rtmp" />
        <data android:scheme="mms" />
        <data android:scheme="tcp" />
        <data android:scheme="udp" />
        <data android:scheme="gopher" />
        <data android:host="*" />
        <data android:pathPattern=".*\\.avi" />
        <data android:pathPattern=".*\\.asf" />
        <data android:pathPattern=".*\\.f4v" />
        <data android:pathPattern=".*\\.flv" />
        <data android:pathPattern=".*\\.mkv" />
        <data android:pathPattern=".*\\.mpeg" />
        <data android:pathPattern=".*\\.mpg" />
        <data android:pathPattern=".*\\.mov" />
        <data android:pathPattern=".*\\.rm" />
        <data android:pathPattern=".*\\.vob" />
        <data android:pathPattern=".*\\.wmv" />
        <data android:pathPattern=".*\\.ts" />
        <data android:pathPattern=".*\\.tp" />
        <data android:pathPattern=".*\\.m3u" />
        <data android:pathPattern=".*\\.m3u8" />
        <data android:pathPattern=".*\\.m4v" />
        <data android:pathPattern=".*\\.mp4" />
    </intent-filter>

    <intent-filter>
        <action android:name="android.intent.action.VIEW" />

        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data android:scheme="rtsp" />
        <data android:mimeType="video/*" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="rtsp" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="http" />
        <data android:mimeType="video/*" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="video/*" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:scheme="file" />
        <data android:mimeType="video/*" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.SEARCH" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>


#视频的SeekBar更新
1.视频的总时长和SeekBar的setMaxt(总时长);
 注意：准备好了的回调后

2.实例化Handler,每秒得到当前视频播放进度，SeekBar.setProgress(当前进度);



#SeekBar的拖拽

1.视频的总时长和SeekBar的setMaxt(总时长);
 注意：准备好了的回调后

2.设置SeekBar状态变化的监听



#注册广播有两种方式：动态注册和静态注册
静态注册：在功能清单文件注册，只要软件安装在手机上，就算软件不启动，也能收到对应的广播；
动态注册：只有注册的代码被执行后，才能收到对应的广播

有五个不能静态注册的广播:

　　android.intent.action.SCREEN_ON

　　android.intent.action.SCREEN_OFF

　　android.intent.action.BATTERY_CHANGED

　　android.intent.action.CONFIGURATION_CHANGED

　　android.intent.action.TIME_TICK


#Activity#onDestory()释放资源的顺序:

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

	同理在onCreate()中初始化时, 先调用 super.onCreate()初始化父类中的信息




#手势识别器

 重写 双击，单击，长按

	detector = new GestureDetector(this, new MySimpleOnGestureListener());

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


    @Override
    public boolean onTouchEvent(MotionEvent event) { 
        //3.把事件传递给手势识别器
        /**
         * 1. 控制面板的隐藏与否:
         * -- 单击屏幕, 若显示则变为隐藏且把消息移除, 若隐藏则变为显示; 且变为显示之后, 还要延迟发送一个隐藏面板的消息
         * -- 手指按下, 移除隐藏消息; 手指抬起, 在延迟发送一个隐藏面板的消息. --> 当面板显示时, 若一直触摸屏幕(滑动进度或声音), 则面板始终显示
         */
        detector.onTouchEvent(event);  //onTouchEvent(),方法中把事件传递给手势识别器
	}


#调节声音
1.实例化AudioManger
 当前的音量
 最大音量

2.SeekBar.setMax(最大音量)
SeekBar.setProgress(当前的音量);

3.设置SeekBar状态变化

# 视频大小调节

原始的 VideoView 并没有设置视频大小的api, 这时得需要自己拓展一个该功能方法: 可以通过改变VideoView的大小来调整视频播放界面的大小.


	/**
	 * 自定义VideoView
	 * 可调整大小
	 */
	public class VideoView extends android.widget.VideoView {
	
	    /**
	     * 在代码中创建的时候一般用这个方法
	     * @param context
	     */
	    public VideoView(Context context) {
	        this(context,null);
	    }
	
	    /**
	     * 当这个类在布局文件的时候，系统通过该构造方法实例化该类
	     * @param context
	     * @param attrs
	     */
	    public VideoView(Context context, AttributeSet attrs) {
	        this(context, attrs,0);
	    }
	
	    /**
	     * 当需要设置样式的时候调用该方法
	     * @param context
	     * @param attrs
	     * @param defStyleAttr
	     */
	    public VideoView(Context context, AttributeSet attrs, int defStyleAttr) {
	        super(context, attrs, defStyleAttr);
	    }
	
	    @Override
	    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
	        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);
	    }
	
	    /**
	     * 设置视频的宽和高
	     * @param videoWidth 指定视频的宽
	     * @param videoHeight 指定视频的高
	     */
	    public void setVideoSize(int videoWidth,int videoHeight){
	        ViewGroup.LayoutParams params = getLayoutParams();
	        params.width = videoWidth;
	        params.height = videoHeight;
	        setLayoutParams(params);
	        // requestLayout(); //重新layout
	    }
	}


等比例缩放视频大小的一个算法:

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

# 系统音量的获取与设置

    // 使用AudioManager获取当前音量值及其范围
    am = (AudioManager) getSystemService(AUDIO_SERVICE);
    currentVoice = am.getStreamVolume(AudioManager.STREAM_MUSIC); // 获取当前音量值
    maxVoice = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);  // 获取音量最大值(0~15)

	// 第三个参数是一个flag, 若为FLAG_SHOW_UI即值为1, 则调整时显示音量条. 为0表示不显示音量条
	am.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0); // 设置音量为progress

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
			// 第三个参数是一个flag, 若为FLAG_SHOW_UI即值为1, 则调整时显示音量条. 为0表示不显示音量条
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
            seekbarVoice.setProgress(0);
        } else {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
            seekbarVoice.setProgress(progress);
            currentVoice = progress; // 记录非静音时的当前音量
        }
    }

	// 滑块监听器中
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



#在屏幕滑动改变声音

往下滑动
float distanceY = startY - endY  < 0;

往上滑动
float distanceY = startY - endY > 0;

 滑动屏幕的距离： 总距离 = 改变声音：音量最大值

 ==> 改变声音 = （滑动屏幕的距离： 总距离）*音量最大值

 ==> 最终声音 = 原来的 + 改变声音；


1.onTouchEvent方法里，在down的时候记录如下信息:
startY
touchRang 总距离
移除消息
mVol


2.在move时,记录如下信息
float endY =  envent.getY();
float distanceY = endY - startY;
改变声音 = （滑动屏幕的距离： 总距离）*音量最大值

最终声音 = 原来的 + 改变声音；

updataVoice();


3.在up的时候重新发消息



#如果让其他软件能调起自己写的播放器

参照系统源代码：

1.在功能清单文件添加下面的意图

        <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="rtsp" />
             </intent-filter>
             <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="video/*" />
                <data android:mimeType="application/sdp" />
             </intent-filter>
             <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="http" />
                <data android:mimeType="video/mp4" />
                <data android:mimeType="video/3gp" />
                <data android:mimeType="video/3gpp" />
                <data android:mimeType="video/3gpp2" />
             </intent-filter>


2.文件或者图片浏览器

	//1.调起系统所有的播放器-隐式意图
	Intent intent = new Intent();
	intent.setDataAndType(Uri.parse("本地/网络视频播放地址"),"video/*");
	context.startActivity(intent);



3.视频播放器就会被调起并且播放

    uri = getIntent().getData();//文件夹，图片浏览器，QQ空间


4.设置播放地址

  videoview.setVideoURI(uri);



#手机连接电脑播放的tomcat的配置

1.在pc上设置wifi热点

2.手机连接到pc共享的wifi上

3.开启电脑的tomcat，并且把一个视频放让tomcat中

4.查看ip地址命令：ipconfig


5.要把视频的固定地址
 http://192.168.10.165:8080/yellow.mp4

修改成无线的ip地址，如下：
http://192.168.191.1:8080/yellow.mp4



#设置监听播放网络视频卡setOnInfoListener

前提：播放视频的时候，网络比较慢才会出现

1.Android2.3，在MediaPlayer引入的监听卡--自定义VideoView，把监听卡封装一下

2.Android4.2.2左右才把监听卡封装在VideoView中


	// Android 4.2 及以上版本 监听视频播放卡-可使用系统的api
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        videoview.setOnInfoListener(new MyOnInfoListener());
    }

	class MyOnInfoListener implements MediaPlayer.OnInfoListener{
	
        @Override
        public boolean onInfo(MediaPlayer mp, int what, int extra) {
            switch (what){
                case MediaPlayer.MEDIA_INFO_BUFFERING_START://视频卡了，拖动卡
                    ll_buffer.setVisibility(View.VISIBLE); //显示视频正在缓冲的视图
                    break;

                case MediaPlayer.MEDIA_INFO_BUFFERING_END://视频卡结束了，拖动卡结束了
                    ll_buffer.setVisibility(View.GONE);
                    break;
            }
            return true;
        }
    }


	// 自定义监听视频卡顿效果(每秒执行一下)
	// 原理:校验播放进度判断是否监听卡. 当前播放进度 - 上一次播放进度 理论值应该等于1s, 若过小的话, 说明卡顿
	// m3u8 直播的视频流. 本质上是把视频分成了一小段一小段的, 所以每次获取currentPosition都为0, 自定义的这种实现监听卡顿效果就不再适用了, 只能换成使用api17之后系统自带的监听器实现了

	int currentPosition = videoview.getCurrentPosition();//0

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

 	precurrentPosition = currentPosition;

	//能得到当前的播放进度的花，建议用校验播放进度判断是否监听卡这种方式, 如果不能得到当前播放进度，可以用系统的监听卡




# 播放出错的几种原因

	1.播放的视频格式不支持--跳转到万能播放器继续播放
	2.播放网络视频的时候，网络中断---1.如果网络确实断了，可以提示用于网络断了；2.网络断断续续的，则重新播放
	3.播放的时候本地文件中间有空白---下载做完成


#Vitamio案例的运行

1.下载地址：
https://www.vitamio.org/Download/

2.下载完成后解压，运行，参照案例集成


#Vitmiao的集成

1.关联Vitamio库

   compile project(':vitamio')

2.把功能清单文件对应的配置拷贝过去
 权限

    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />

  配置

    <!-- Don't forgot InitActivity -->
    <activity
        android:name="io.vov.vitamio.activity.InitActivity"
        android:configChanges="orientation|screenSize|smallestScreenSize|keyboard|keyboardHidden|navigation"
        android:launchMode="singleTop"
        android:theme="@android:style/Theme.NoTitleBar"
        android:windowSoftInputMode="stateAlwaysHidden" />


3.把系统SystemVideoPlayer复制一份，改名VitamioVideoPlayer
  导入的包全部换成是Vitamio的包，MediaPlayer,VideoView

自定义VitamioVideoView 继承Vitamio的VideoView



4.布局文件activity_system_video_player.xml复制一份改名activity_vitamio_video_player.xml,并且把里面的VideoView替换成Vitamio的VitamioVideoView



5.初始化Vitamio库，在布局文件加载之前

   Vitamio.isInitialized(this);



6.当系统播放器播放出错的时候跳转到VitamioVideoPlayer播放
 疑问：能否直接用Vitamio播放器播放呢？ 可以. 那为什么先用系统的播发器,播放出错的时候才使用万能播放器呢? 原因在于系统播放器的效率要比第三方的要高. 万能播放器虽然各种格式的视频都可以播放, 但是同样开销也很大

 注意：a,把数据传入VtaimoVideoPlayer播放器
       b,关闭系统播放器


# 解决 Vitamio 集成后在小米手机播放崩溃Bug, 在模拟器中却正常的现象
把项目中的gradle中的 targetSdkVersion 改成和样例中的一样即可
eg: targetSdkVersion 22


# 去掉两个Activity切换间的默认动画

    <style name="noAnimation_Theme" parent="android:Theme">
            <item name="android:windowAnimationStyle">@style/noAnimation</item>
            <item name="android:windowNoTitle">true</item>
            <item name="android:windowFullscreen">true</item>
            <item name="android:windowContentOverlay">@null</item>
    </style>

    <style name="noAnimation">
            <item name="android:activityOpenEnterAnimation">@null</item>
            <item name="android:activityOpenExitAnimation">@null</item>
            <item name="android:activityCloseEnterAnimation">@null</item>
            <item name="android:activityCloseExitAnimation">@null</item>
            <item name="android:taskOpenEnterAnimation">@null</item>
            <item name="android:taskOpenExitAnimation">@null</item>
            <item name="android:taskCloseEnterAnimation">@null</item>
            <item name="android:taskCloseExitAnimation">@null</item>
            <item name="android:taskToFrontEnterAnimation">@null</item>
            <item name="android:taskToFrontExitAnimation">@null</item>
            <item name="android:taskToBackEnterAnimation">@null</item>
            <item name="android:taskToBackExitAnimation">@null</item>

    </style>

	然后在清单文件中给Activity配置以上主题即可


让Activity在桌面显示图标，并且点击图标的时候进入软件

	<--一个APP可以有多个LAUNCHER, 这时安装程序的时候, 系统会优先启动清单列表中的第一个LAUNCHER界面. 而且桌面会形成多个图标. label属性还可以更改名字-->
	<category android:name="android.intent.category.LAUNCHER" />