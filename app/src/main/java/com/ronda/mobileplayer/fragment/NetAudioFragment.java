package com.ronda.mobileplayer.fragment;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.ronda.mobileplayer.base.BaseFragment;
import com.ronda.mobileplayer.base.BasePager;
import com.ronda.mobileplayer.pager.NetAudioPager;
import com.ronda.mobileplayer.pager.VideoPager;

/**
 * Created by Ronda on 2018/4/10.
 */

public class NetAudioFragment extends BaseFragment {

    public static final String ARGUMENT = "argument";

    private BasePager pager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            String s = getArguments().getString(ARGUMENT);
        }

        pager = new NetAudioPager(getActivity());
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        if (pager != null && !pager.isInitData) { //避免重复初始化数据, 节省流量
            pager.initData();//联网请求或者绑定数据
            pager.isInitData = true;
        }

        if (pager != null) {
            //各个页面的视图
            return pager.rootView;
        }

        return super.onCreateView(inflater, container, savedInstanceState);
    }


    public static BaseFragment newInstance(String args) {
        Bundle bundle = new Bundle();
        bundle.putString(ARGUMENT, args);
        NetAudioFragment fragment = new NetAudioFragment();
        fragment.setArguments(bundle);
        return fragment;
    }
}
