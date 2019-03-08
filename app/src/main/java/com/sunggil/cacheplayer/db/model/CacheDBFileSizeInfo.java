package com.sunggil.cacheplayer.db.model;

public class CacheDBFileSizeInfo {
	String url;
	int totalFileSize;
	int headerSize;
	
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public int getTotalFileSize() {
		return totalFileSize;
	}
	public void setTotalFileSize(int totalFileSize) {
		this.totalFileSize = totalFileSize;
	}
	public int getHeaderSize() {
		return headerSize;
	}
	public void setHeaderSize(int headerSize) {
		this.headerSize = headerSize;
	}
}
