package com.ronda.mobileplayer.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;

import com.ronda.mobileplayer.R;
import com.socks.library.KLog;

/**
 * 注意点:
 * 1. 即使多次调用 startMainActivity() 也只执行跳转一次. 添加flag
 * 2. 启动时立即退出,避免2s后又自动跳转. 所以退出时要清空handler中的所有消息
 */

public class SplashActivity extends Activity {

    private static final String TAG = SplashActivity.class.getSimpleName();//"SplashActivity"

    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // 延迟2s跳转至MainActivity. 主线程中执行
                startMainActivity();

                KLog.e( "当前线程名称==" + Thread.currentThread().getName());
            }
        }, 2000);
    }

    private boolean isStartMain = false; // 保证跳转操作只执行一次
    /**
     * 跳转到主页面，并且把当前页面关闭掉
     */
    private void startMainActivity() {
        if(!isStartMain){
            isStartMain = true;
            Intent intent = new Intent(this,MainActivity.class);
            startActivity(intent);
            //关闭当前页面
            finish();
        }

    }

    /**
     * 触摸立即跳转. 由于onTouchEvent()在触摸时会多次回调, 所以此时isStartMain这个flag就起到了限制作用.
     * 还有另一种解决方法: 把MainActivity设置成单例的SingleTask/SingleInstance
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        KLog.d("onTouchEvent");
        startMainActivity();
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDestroy() {
        //把所有的消息和回调移除. 避免启动时立即退出, 延迟启动的逻辑再次执行
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
