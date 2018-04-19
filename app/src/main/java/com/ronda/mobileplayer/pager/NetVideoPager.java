package com.ronda.mobileplayer.pager;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ronda.mobileplayer.R;
import com.ronda.mobileplayer.activity.SystemVideoPlayer;
import com.ronda.mobileplayer.adapter.NetVideoPagerAdapter;
import com.ronda.mobileplayer.base.BasePager;
import com.ronda.mobileplayer.domain.MediaItem;
import com.ronda.mobileplayer.utils.CacheUtils;
import com.ronda.mobileplayer.utils.Constants;
import com.ronda.mobileplayer.utils.LogUtil;
import com.ronda.mobileplayer.view.XListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xutils.common.Callback;
import org.xutils.http.RequestParams;
import org.xutils.view.annotation.ViewInject;
import org.xutils.x;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;


/**
 * 网络视频页面
 */
public class NetVideoPager extends BasePager {

    @ViewInject(R.id.listview)
    private XListView mListview;

    @ViewInject(R.id.tv_nonet)
    private TextView mTv_nonet;

    @ViewInject(R.id.pb_loading)
    private ProgressBar mProgressBar;

    /**
     * 装数据集合
     */
    private ArrayList<MediaItem> mediaItems;

    private NetVideoPagerAdapter adapter;

    /**
     * 判断是否是加载更多的数据
     */
    private boolean isLoadMoreData = false;


    public NetVideoPager(Context context) {
        super(context);
    }

    /**
     * 初始化当前页面的控件，由父类调用
     *
     * @return
     */
    @Override
    public View initView() {
        View view = View.inflate(context, R.layout.netvideo_pager, null);
        //第一个参数是：NetVideoPager.this,第二个参数：布局
        x.view().inject(NetVideoPager.this, view);
        mListview.setOnItemClickListener(new MyOnItemClickListener());
        mListview.setPullLoadEnable(true);
        mListview.setXListViewListener(new MyIXListViewListener());
        return view;
    }

    @Override
    public void initData() {
        super.initData();
        LogUtil.e("网络视频的数据被初始化了。。。");
        String saveJson = CacheUtils.getString(context, Constants.NET_URL);
        if (!TextUtils.isEmpty(saveJson)) {
            processData(saveJson);
        }
        getDataFromNet();
    }

    /**
     * 获取下拉刷新的数据
     */
    private void getDataFromNet() {
        //联网
        //视频内容
        RequestParams params = new RequestParams(Constants.NET_URL);
        x.http().get(params, new Callback.CommonCallback<String>() {
            @Override
            public void onSuccess(String result) { //主线程
                LogUtil.e("onSuccess-->" + result);
                CacheUtils.putString(context, Constants.NET_URL, result); //缓存数据
                processData(result);
            }

            @Override
            public void onError(Throwable ex, boolean isOnCallback) {
                LogUtil.e("onError-->" + ex.getMessage());
                showData();
            }

            @Override
            public void onCancelled(CancelledException cex) {
                LogUtil.e("onCancelled-->" + cex.getMessage());
            }

            @Override
            public void onFinished() {
                LogUtil.e("onFinished");
            }
        });
    }


    /**
     * 获取加载更多数据. 理论上应该专门有一个后台接口
     */
    private void getMoreDataFromNet() {
        //联网
        //视频内容
        RequestParams params = new RequestParams(Constants.NET_URL);
        x.http().get(params, new Callback.CommonCallback<String>() {
            @Override
            public void onSuccess(String result) {
                LogUtil.e("onSuccess-->" + result);
                isLoadMoreData = true;
                //主线程
                processData(result);
            }

            @Override
            public void onError(Throwable ex, boolean isOnCallback) {
                LogUtil.e("onError-->" + ex.getMessage());
                isLoadMoreData = false;
            }

            @Override
            public void onCancelled(CancelledException cex) {
                LogUtil.e("onCancelled-->" + cex.getMessage());
                isLoadMoreData = false;
            }

            @Override
            public void onFinished() {
                LogUtil.e("onFinished");
                isLoadMoreData = false;
            }
        });
    }


    class MyOnItemClickListener implements AdapterView.OnItemClickListener {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            //3.传递列表数据-对象-序列化
            Intent intent = new Intent(context, SystemVideoPlayer.class);
            Bundle bundle = new Bundle();
            bundle.putSerializable("videolist", mediaItems);
            intent.putExtras(bundle);
            intent.putExtra("position", position - 1); // 去掉ListView的header
            context.startActivity(intent);
        }
    }

    /**
     * 获取后台数据后,需要处理数据
     * 该数据分为: 下拉刷新数据 和 加载更多数据
     */
    private void processData(String json) {

        if (!isLoadMoreData) { // 下拉刷新
            mediaItems = parseJson(json);
            showData();
        } else {//加载更多
            isLoadMoreData = false;
            mediaItems.addAll(parseJson(json)); //要把得到更多的数据，添加到原来的集合中
            //刷新适配器
            adapter.notifyDataSetChanged();
            onLoad();
        }
    }

    private void showData() {
        //设置适配器
        if (mediaItems != null && mediaItems.size() > 0) {
            //有数据
            //设置适配器
            adapter = new NetVideoPagerAdapter(context, mediaItems);
            mListview.setAdapter(adapter);
            onLoad();
            //把文本隐藏
            mTv_nonet.setVisibility(View.GONE);
        } else {
            //没有数据
            //文本显示
            mTv_nonet.setVisibility(View.VISIBLE);
        }

        //ProgressBar隐藏
        mProgressBar.setVisibility(View.GONE);
    }


    /**
     * XListView 无论是下拉刷新还是加载更多, 只要获取到数据后,需要调用此方法
     */
    private void onLoad() {
        mListview.stopRefresh();
        mListview.stopLoadMore();
        mListview.setRefreshTime("更新时间:" + getSysteTime());
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

    /**
     * 解决json数据：
     * 1.用系统接口解析json数据
     * 2.使用第三方解决工具（Gson,fastjson）
     *
     * @param json
     * @return
     */
    private ArrayList<MediaItem> parseJson(String json) {
        ArrayList<MediaItem> mediaItems = new ArrayList<>();
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONArray jsonArray = jsonObject.optJSONArray("trailers");
            if (jsonArray != null && jsonArray.length() > 0) {
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObjectItem = (JSONObject) jsonArray.get(i);
                    if (jsonObjectItem != null) {

                        MediaItem mediaItem = new MediaItem();

                        String movieName = jsonObjectItem.optString("movieName");//name
                        mediaItem.setName(movieName);

                        String videoTitle = jsonObjectItem.optString("videoTitle");//desc
                        mediaItem.setDesc(videoTitle);

                        String imageUrl = jsonObjectItem.optString("coverImg");//imageUrl
                        mediaItem.setImageUrl(imageUrl);

                        String hightUrl = jsonObjectItem.optString("hightUrl");//data
                        mediaItem.setData(hightUrl);

                        //把数据添加到集合
                        mediaItems.add(mediaItem);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return mediaItems;
    }

    // XListView 上拉刷新和下拉加载更多的回调
    class MyIXListViewListener implements XListView.IXListViewListener {
        @Override
        public void onRefresh() {
            getDataFromNet();
        }

        @Override
        public void onLoadMore() {
            getMoreDataFromNet();
        }
    }
}
