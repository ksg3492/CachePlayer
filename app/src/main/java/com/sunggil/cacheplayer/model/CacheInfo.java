package com.sunggil.cacheplayer.model;

public class CacheInfo {
    public static final int PLAY_MODE_SINGLE_FILE = 0;
    public static final int PLAY_MODE_DIVIDE_FILE = 1;

//    CacheInfo.playerType == getViewId == CacheFunctionParam.playerType

    //common
    boolean isLocalFile = false;
    String fileName = "";
    String filePath = "";
    int playMode = PLAY_MODE_SINGLE_FILE;
    String playerType = "";
    boolean isLastMemory = false;
    int progress = 0;

    //case PLAY_MODE_DIVIDE_FILE
    int index = 0;

    public boolean isLocalFile() {
        return isLocalFile;
    }

    public void setLocalFile(boolean localFile) {
        isLocalFile = localFile;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getPlayMode() {
        return playMode;
    }

    public void setPlayMode(int playMode) {
        this.playMode = playMode;
    }

    public String getPlayerType() {
        return playerType;
    }

    public void setPlayerType(String playerType) {
        this.playerType = playerType;
    }

    public boolean isLastMemory() {
        return isLastMemory;
    }

    public void setLastMemory(boolean lastMemory) {
        isLastMemory = lastMemory;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void init() {
        this.isLocalFile = false;
        this.fileName = "";
        this.filePath = "";
        this.playMode = PLAY_MODE_SINGLE_FILE;
        this.playerType = "";
        this.isLastMemory = false;
        this.progress = 0;
        this.index = 0;
    }
}
