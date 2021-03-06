package com.sclouds.effect.utils;

import android.util.Log;

import com.orangefilter.OrangeFilter;

public class FilterHelper {
    private static final String TAG = "FilterHelper";

    private int mContext = 0;
    private int mEffect = 0;
    private OrangeFilter.OF_EffectInfo mInfo;
    private boolean mReady = false;
    private OnFilterChangeListener mOnFilterChangeListener;

    public FilterHelper() {

    }

    public void setEffect(int context, int effect) {
        mContext = context;
        mEffect = effect;

        if (mEffect != 0) {
            mInfo = new OrangeFilter.OF_EffectInfo();
            OrangeFilter.getEffectInfo(mContext, mEffect, mInfo);

            if (mInfo.filterCount != 1) {
                Log.e(TAG, "effect info error with filter count: " + mInfo.filterCount);
            }

            mReady = true;
        } else {
            mInfo = null;
            mReady = false;
        }

        if (mOnFilterChangeListener != null) {
            mOnFilterChangeListener.onFilterChange();
        }
    }

    public void clearEffect() {
        mContext = 0;
        mEffect = 0;
        mInfo = null;
        mReady = false;
    }

    public boolean isReady() {
        return mReady;
    }

    private OrangeFilter.OF_Param getFilterParam() {
        if (mInfo != null) {
            int filter = mInfo.filterList[0];
            return OrangeFilter.getFilterParamData(mContext, filter, "Intensity");
        }
        return null;
    }

    public int getFilterIntensity() {
        OrangeFilter.OF_Paramf param = (OrangeFilter.OF_Paramf) getFilterParam();
        if (param != null) {
            return (int) (param.val * 100);
        }
        return 0;
    }

    public void setFilterIntensity(int value) {
        OrangeFilter.OF_Paramf param = (OrangeFilter.OF_Paramf) getFilterParam();
        if (param != null) {
            param.val = value / 100.0f;

            int filter = mInfo.filterList[0];
            OrangeFilter.setFilterParamData(mContext, filter, param.name, param);
        }
    }

    public void setOnFilterChangeListener(OnFilterChangeListener listener) {
        mOnFilterChangeListener = listener;
    }

    public interface OnFilterChangeListener {
        void onFilterChange();
    }
}
