package com.ronda.mobileplayer.pager;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;


import com.ronda.mobileplayer.R;
import com.ronda.mobileplayer.activity.SystemVideoPlayer;
import com.ronda.mobileplayer.adapter.VideoPagerAdapter;
import com.ronda.mobileplayer.base.BasePager;
import com.ronda.mobileplayer.domain.MediaItem;
import com.ronda.mobileplayer.utils.LogUtil;

import java.util.ArrayList;

/**
 * 作用：本地视频页面
 */
public class VideoPager extends BasePager implements AdapterView.OnItemClickListener {

    private ListView listview;
    private TextView tv_nomedia;
    private ProgressBar pb_loading;

    private VideoPagerAdapter videoPagerAdapter;

    /**
     * 装数据集合
     */
    private ArrayList<MediaItem> mediaItems;


    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (mediaItems != null && mediaItems.size() > 0) {
                //有数据, 设置适配器
                videoPagerAdapter = new VideoPagerAdapter(context, mediaItems, true);
                listview.setAdapter(videoPagerAdapter);
                //把文本隐藏
                tv_nomedia.setVisibility(View.GONE);
            } else {
                //没有数据, 文本显示
                tv_nomedia.setVisibility(View.VISIBLE);
            }


            //ProgressBar隐藏
            pb_loading.setVisibility(View.GONE);
        }
    };


    public VideoPager(Context context) {
        super(context);
    }


    /**
     * 初始化当前页面的控件，由父类调用
     *
     * @return
     */
    @Override
    public View initView() {
        View view = View.inflate(context, R.layout.video_pager, null);
        listview = (ListView) view.findViewById(R.id.listview);
        tv_nomedia = (TextView) view.findViewById(R.id.tv_nomedia);
        pb_loading = (ProgressBar) view.findViewById(R.id.pb_loading);
        //设置ListView的Item的点击事件
        listview.setOnItemClickListener(this);
        return view;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

        MediaItem mediaItem = mediaItems.get(position);
//            Toast.makeText(context, "mediaItem=="+mediaItem.toString(), Toast.LENGTH_SHORT).show();

        /*
        //1.调起系统所有的播放-隐式意图
        Intent intent = new Intent();
        intent.setDataAndType(Uri.parse(mediaItem.getData()), "video/*");
        context.startActivity(intent);
        */

        /*
        //2.调用自己写的播放器-显示意图--一个播放地址 即个
        // SystemVideoPlayer 中使用getIntent().getData();获取URI, 然后使用videoview.setVideoURI(uri);设置URI;
        // 还可以使用 videoview.setMediaController(new MediaController(this)); 添加一个控制区域
        Intent intent = new Intent(context, SystemVideoPlayer.class);
        intent.setDataAndType(Uri.parse(mediaItem.getData()), "video/*");
        context.startActivity(intent);
        */

        //3.传递列表数据-对象-序列化
        Intent intent = new Intent(context, SystemVideoPlayer.class);
//        Bundle bundle = new Bundle();
//        bundle.putSerializable("videolist", mediaItems);
//        intent.putExtras(bundle);
        // 也可以直接给调用intent.putExtra()方法. 本质上是一样的
        intent.putExtra("videolist", mediaItems);
        intent.putExtra("position", position);
        context.startActivity(intent);
    }


    @Override
    public void initData() {
        super.initData();
        LogUtil.e("本地视频的数据被初始化了。。。");
        //加载本地视频数据
        getDataFromLocal();
    }

    /**
     * 从本地的sdcard得到数据
     * //1.递归遍历sdcard,后缀名. 不可取
     * //2.从内容提供者里面获取视频(数据库中获取) MediaScanner(媒体扫描器) (遇到开机广播, sd卡装载广播开始扫描, 把媒体文件记录在数据库中, 通过内容提供者供其他程序使用)
     * //3.如果是6.0的系统，动态获取读取sdcard的权限
     */
    private void getDataFromLocal() {

        new Thread() {
            @Override
            public void run() {
                super.run();

//                isGrantExternalRW((Activity) context);
                // SystemClock.sleep(2000);// 测试加载进度
                mediaItems = new ArrayList<>();
                ContentResolver resolver = context.getContentResolver();
                Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                String[] projection = {
                        MediaStore.Video.Media.DISPLAY_NAME, //视频文件在sdcard的名称
                        MediaStore.Video.Media.DURATION,     //视频总时长
                        MediaStore.Video.Media.SIZE,         //视频的文件大小
                        MediaStore.Video.Media.DATA,         //视频的绝对地址
                        MediaStore.Video.Media.ARTIST,       //歌曲的演唱者, 视频不一定有

                };
                Cursor cursor = resolver.query(uri, projection, null, null, null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {

                        MediaItem mediaItem = new MediaItem();

                        String name = cursor.getString(0);  //视频的名称
                        long duration = cursor.getLong(1);  //视频的时长
                        long size = cursor.getLong(2);      //视频的文件大小
                        String data = cursor.getString(3);  //视频的播放地址
                        String artist = cursor.getString(4);//艺术家

                        mediaItem.setName(name);
                        mediaItem.setDuration(duration);
                        mediaItem.setSize(size);
                        mediaItem.setData(data);
                        mediaItem.setArtist(artist);

                        mediaItems.add(mediaItem);
                    }
                    cursor.close();
                }
                //获取媒体信息完成后, 使用Handler发消息通知
                handler.sendEmptyMessage(0);
            }
        }.start();

    }

    /**
     * 解决安卓6.0以上版本不能读取外部存储权限的问题
     *
     * @param activity
     * @return
     */
    public static boolean isGrantExternalRW(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity.checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            activity.requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 1);

            return false;
        }

        return true;
    }


}
