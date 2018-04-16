package com.ronda.mobileplayer.activity;

import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.ronda.mobileplayer.R;
import com.ronda.mobileplayer.base.BasePager;
import com.ronda.mobileplayer.fragment.AudioFragment;
import com.ronda.mobileplayer.fragment.NetAudioFragment;
import com.ronda.mobileplayer.fragment.NetVideoFragment;
import com.ronda.mobileplayer.fragment.VideoFragment;
import com.ronda.mobileplayer.pager.AudioPager;
import com.ronda.mobileplayer.base.BaseFragment;
import com.ronda.mobileplayer.pager.NetAudioPager;
import com.ronda.mobileplayer.pager.NetVideoPager;
import com.ronda.mobileplayer.pager.VideoPager;

import java.util.ArrayList;

/**
 * 主界面
 * 1. 这里虽继承自FragmentActivity, 然而并没有使用到 FragmentActivity 的特性
 */
public class MainActivity extends FragmentActivity implements RadioGroup.OnCheckedChangeListener {


    private RadioGroup rg_bottom_tag;

    /**
     * 页面的集合
     */
    private ArrayList<BasePager> basePagers;

    /**
     * fragment的集合
     */
    private ArrayList<BaseFragment> fragmentList ;

    /**
     * 选中的位置
     */
    private int position;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        rg_bottom_tag = (RadioGroup) findViewById(R.id.rg_bottom_tag);

//        basePagers = new ArrayList<>();
//        basePagers.add(new VideoPager(this));//添加本地视频页面-0
//        basePagers.add(new AudioPager(this));//添加本地音乐页面-1
//        basePagers.add(new NetVideoPager(this));//添加网络视频页面-2
//        basePagers.add(new NetAudioPager(this));//添加网络音频页面-3
//        for (int i = 0; i < basePagers.size(); i++) {
//            fragmentList.add(BaseFragment.newInstance(basePagers.get(i)));
//        }

        fragmentList = new ArrayList<>();

        /*
        fragmentList.add(BaseFragment.newInstance(new VideoPager(this)));    //添加本地视频页面-0
        fragmentList.add(BaseFragment.newInstance(new AudioPager(this)));    //添加本地音乐页面-1
        fragmentList.add(BaseFragment.newInstance(new NetVideoPager(this))); //添加网络视频页面-2
        fragmentList.add(BaseFragment.newInstance(new NetAudioPager(this))); //添加网络音频页面-3
        */

        fragmentList.add(new VideoFragment());    //添加本地视频页面-0
        fragmentList.add(new AudioFragment());    //添加本地音乐页面-1
        fragmentList.add(new NetVideoFragment()); //添加网络视频页面-2
        fragmentList.add(new NetAudioFragment()); //添加网络音频页面-3



        //设置RadioGroup的监听
        rg_bottom_tag.setOnCheckedChangeListener(this);
        rg_bottom_tag.check(R.id.rb_video);//默认选中首页

    }

    // RadioGroup 的监听器
    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        switch (checkedId) {
            default:
                position = 0;
                break;
            case R.id.rb_audio://音频
                position = 1;
                break;
            case R.id.rb_net_video://网络视频
                position = 2;
                break;
            case R.id.rb_netaudio://网络音频
                position = 3;
                break;
        }
        setFragment();
    }

    /**
     * 把页面添加到Fragment中
     */
    private void setFragment() {
        //1.得到FragmentManger
        FragmentManager manager = getSupportFragmentManager();
        //2.开启事务
        FragmentTransaction ft = manager.beginTransaction();

        //3.替换
//        ft.replace(R.id.fl_main_content, new Fragment() {
//            @Nullable
//            @Override
//            public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//                BasePager basePager = getBasePager();
//                if (basePager != null) {
//                    //各个页面的视图
//                    return basePager.rootView;
//                }
//                return null;
//            }
//        });
        ft.replace(R.id.fl_main_content, fragmentList.get(position));
        //4.提交事务
        ft.commit();
    }

//    /**
//     * 根据位置得到对应的页面
//     *
//     * @return
//     */
//    private BasePager getBasePager() {
//        BasePager basePager = basePagers.get(position);
//        if (basePager != null && !basePager.isInitData) { //避免重复初始化数据, 节省流量
//            basePager.initData();//联网请求或者绑定数据
//            basePager.isInitData = true;
//        }
//        return basePager;
//    }


    /**
     * 是否已经退出
     */
    private boolean isExit = false;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (position != 0) {//不是第一页面
                position = 0;
                rg_bottom_tag.check(R.id.rb_video);//首页
                return true;
            } else if (!isExit) {
                isExit = true;
                Toast.makeText(MainActivity.this, "再按一次推出", Toast.LENGTH_SHORT).show();
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        isExit = false;
                    }
                }, 2000);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
