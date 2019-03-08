package com.sunggil.cacheplayer.model;

public class CacheFunctionParam {
    String playerType;
    String rootFolderName;
    String folderName;
    int playMode;

    public CacheFunctionParam(String playerType, String rootFolderName, String folderName, int playMode) {
        this.playerType = playerType;
        this.rootFolderName = rootFolderName;
        this.folderName = folderName;
        this.playMode = playMode;
    }

    public String getPlayerType() {
        return playerType;
    }

    public void setPlayerType(String playerType) {
        this.playerType = playerType;
    }

    public String getRootFolderName() {
        return rootFolderName;
    }

    public void setRootFolderName(String rootFolderName) {
        this.rootFolderName = rootFolderName;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public int getPlayMode() {
        return playMode;
    }

    public void setPlayMode(int playMode) {
        this.playMode = playMode;
    }
}
