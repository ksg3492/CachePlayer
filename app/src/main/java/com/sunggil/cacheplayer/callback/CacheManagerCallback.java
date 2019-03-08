package com.sunggil.cacheplayer.callback;

public interface CacheManagerCallback {
    void onPrepare(boolean init, String url, int index, String playerType);
    void onInfoChanged(int totalFileSize, int headerSize);
    void onBufferingFinish(String url, int index, String playerType);
    int getFileTotalSize();
    void ChangeLastMemory(boolean changedLastMemory);
    void initRequestNextSection();
    void onError();
    void onCompleteCache();
    void setProgressNextDownload(boolean b);
    void onEmptyCacheParams();
}