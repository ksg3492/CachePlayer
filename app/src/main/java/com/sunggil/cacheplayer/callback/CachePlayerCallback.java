package com.sunggil.cacheplayer.callback;

public interface CachePlayerCallback {
    void onEmptyCacheParams();

    void onPreparedCache(int duration);
    void onCompleteCache();
    void onPlayCache(int curTime);
    void onPauseCache(int curTime);
    void onSeekToCache(int curTime);
    void onTimeoutCache();
    void onUpdateCache(int curTime);
    void onPlayViaMediaPlayer(String path);
    void onLoadingWebView();
    void onChangeLastMemory(int i);
}