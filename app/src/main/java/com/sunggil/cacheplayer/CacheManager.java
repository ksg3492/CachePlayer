package com.sunggil.cacheplayer;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.util.Log;

import com.sunggil.cacheplayer.callback.CacheManagerCallback;
import com.sunggil.cacheplayer.db.CacheDBManager;
import com.sunggil.cacheplayer.db.model.CacheDBFileSizeInfo;
import com.sunggil.cacheplayer.db.model.CacheDBInfo;
import com.sunggil.cacheplayer.model.CacheFunctionParam;
import com.sunggil.cacheplayer.model.CacheInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

class CacheManager {
    private final String TAG = "CacheManager";

    private Context mContext;
    private CacheManagerCallback callback;
    private PrepareThread prepareThread;

    private CacheDBManager dbManager = null;

    private final String SEED_KEY = "CACHE_CRYPTO_KEY";

    private final int CACHE_MEM_MAX_LIMIT_SINGLE = 250 * 1024 * 1024;
    private final int CACHE_MEM_MAX_LIMIT_DIVIDE = 50 * 1024 * 1024;

    private final int CACHE_MEM_MIN_LIMIT_SINGLE = 10 * 1024 * 1024;
    private final int CACHE_MEM_MIN_LIMIT_DIVIDE = 5 * 1024 * 1024;

    private final int CACHE_MIN_FILE_SIZE = 1024;

    private final int STREAMING_BUFFER_BLOCK_SIZE = 3 * 1024 * 1024;

    private final int STREAMING_BUFFER_MIN_START_SIZE = 512 * 1024;

    DownloadThread downloadThread;
    DownloadPrepareThread downloadPrepareThread;

    private HashMap<String, CacheFunctionParam> functionHashMap = null;

    public CacheManager(Context context, String authKey, CacheManagerCallback callback, HashMap<String, CacheFunctionParam> param) {
        mContext = context;
        this.callback = callback;

        if (param.values().size() == 0) {
            callback.onEmptyCacheParams();
            return;
        }

        this.functionHashMap = param;
        init(mContext, authKey);
    }

    public void init(Context context, String authKey) {
        dbManager = new CacheDBManager(context, authKey);
        new Thread(new Runnable() {
            @Override
            public void run() {
                dbManager.printDB();
            }
        }).start();

        createCacheDir();
        new CleanupCacheFileThread().start();
    }

    public void closeDB () {
        if (dbManager != null) {
            dbManager.closeDB();
        }
    }

    public void prepare(boolean init, CacheInfo info, int index, boolean isBuffer, boolean isLastmemory, boolean isRequestNext) {
        Log.e(TAG, "prepare()");
        prepareThread = new PrepareThread(init, info, index, isBuffer, isLastmemory, isRequestNext);
        prepareThread.start();
    }

    private class PrepareThread extends Thread {
        private boolean init;
        private String fileName;
        private String path;
        private int index;
        private int playMode;
        private String playerType;
        private boolean isBuffering;
        private boolean isLastmemory;
        private boolean isRequestNext;

        PrepareThread(boolean init, CacheInfo info, int index, boolean isBuffering, boolean isLastmemory, boolean isRequestNext) {
            this.init = init;
            this.fileName = fileName + index;
            this.path = info.getFilePath();
            this.index = index;
            this.playMode = info.getPlayMode();
            this.playerType = info.getPlayerType();
            this.isBuffering = isBuffering;
            this.isLastmemory = isLastmemory;
            this.isRequestNext = isRequestNext;
        }

        @Override
        public void run() {
            boolean isCached = false;
            String filePath = "";
            String encFileName = "";

            String encFileInfo = getEncryptFileName(fileName);
            CacheDBInfo cacheDBInfo = getCacheInfo(encFileInfo);

            if (cacheDBInfo != null) {
                Log.e(TAG, "cacheInfo != null");
                encFileName = cacheDBInfo.getFileName();

                for (CacheFunctionParam param : functionHashMap.values()) {
                    if (playerType.equals(param.getPlayerType())) {
                        filePath = Environment.getExternalStorageDirectory() + "/" + param.getRootFolderName() + "/" + param.getFolderName() + "/" + encFileName;

                        break;
                    }
                }

                File file = new File(filePath);

                if (file.exists()) {
                    Log.e(TAG, "file.exists()");
                    if (file.length() == 0) {
                        Log.e(TAG, "file.length() == 0");
                        dbManager.delete(encFileName);
                        try {
                            file.delete();
                        } catch (Exception e) {
                            Log.e(TAG, "", e);
                        }
                    } else {
                        Log.e(TAG, "file.length() != 0");
                        isCached = true;
                        fileName = encFileName;
                    }
                } else {
                    Log.e(TAG, "file NOT exists()");
                    dbManager.delete(encFileName);
                }
            } else {
                callback.ChangeLastMemory(true);
                Log.e(TAG, "cacheInfo == null");
            }

            Log.e(TAG, "isCached " + isCached);
            if (isCached) {
                Log.e(TAG, "playMode : " + playMode);
//                if (playerType == PLAYER_TYPE_PODCAST || playerType == PLAYER_TYPE_NEWS) {
                if (playMode == CacheInfo.PLAY_MODE_DIVIDE_FILE) {
                    if (getTotalFileSize(path, false, isRequestNext)) {
                        isTimeout = false;
                        dbManager.delete(encFileName);
                        File file = new File(filePath);

                        try {
                            Log.e(TAG, "delete file");
                            file.delete();
                        } catch (Exception e) {
                            Log.e(TAG, "", e);
                        }

                        mTimerHandler.removeMessages(0);

                        downloadPrepareThread = null;
                        downloadPrepareThread = new DownloadPrepareThread(init, encFileInfo, path, index, playMode, playerType, isBuffering, isLastmemory, isRequestNext);

                        Message msg = new Message();
                        msg.what = 0;
                        msg.arg1 = 10;
                        mTimerHandler.sendMessage(msg);
                        return;
                    }
                    isTimeout = false;
                } else if (playMode == CacheInfo.PLAY_MODE_SINGLE_FILE) {
                    try {
                        File file = new File(filePath);
                        PasswordCrypto.decrypt(SEED_KEY, file);
//                        PasswordCrypto.decryptToFile(SEED_KEY,
//                                Environment.getExternalStorageDirectory() + CACHE_PATH_MELON,
//                                Environment.getExternalStorageDirectory() + CACHE_PATH_TEMP,
//                                encFileName);
                    } catch (Exception e) {
                        mTimerHandler.removeMessages(0);

                        downloadPrepareThread = null;
                        downloadPrepareThread = new DownloadPrepareThread(init, encFileInfo, path, index, playMode, playerType, isBuffering, isLastmemory, isRequestNext);

                        Message msg = new Message();
                        msg.what = 0;
                        msg.arg1 = 10;
                        mTimerHandler.sendMessage(msg);
                        return;
                    }

//                    filePath = Environment.getExternalStorageDirectory() + CACHE_PATH_TEMP + cacheDBInfo.getFileName();
                }

                if (isBuffering) {
                    callback.onBufferingFinish(filePath, index, playerType);
                } else {
                    callback.onPrepare(init, filePath, index, playerType);
                }
            } else {
                mTimerHandler.removeMessages(0);

                downloadPrepareThread = null;
                downloadPrepareThread = new DownloadPrepareThread(init, encFileInfo, path, index, playMode, playerType, isBuffering, isLastmemory, isRequestNext);

                Message msg = new Message();
                msg.what = 0;
                msg.arg1 = 10;
                mTimerHandler.sendMessage(msg);
            }
        }
    }

    class DownloadPrepareThread extends Thread {
        boolean init;
        String encFileInfo;
        String path;
        int index;
        int playMode;
        String playerType;
        private boolean isBuffering;
        private boolean isLastmemory;
        private boolean isRequestNext;

        public DownloadPrepareThread(boolean init, String encFileInfo, String path, int index, int playMode, String playerType, boolean isBuffering, boolean isLastmemory, boolean isRequestNext) {
            Log.e(TAG, "DownloadPrepareThread()");
            this.init = init;
            this.encFileInfo = encFileInfo;
            this.path = path;
            this.index = index;
            this.playMode = playMode;
            this.playerType = playerType;
            this.isBuffering = isBuffering;
            this.isLastmemory = isLastmemory;
            this.isRequestNext = isRequestNext;

            Log.e(TAG, "init : " + init);
            Log.e(TAG, "encFileInfo : " + encFileInfo);
            Log.e(TAG, "index : " + index);
            Log.e(TAG, "playMode : " + playMode);
            Log.e(TAG, "playerType : " + playerType);
        }

        @Override
        public void run() {
            Log.e(TAG, "DownloadPrepareThread run()");
            downloadThread = null;
            downloadThread = new DownloadThread(init, encFileInfo, path, index, playMode, playerType, isBuffering, isLastmemory, isRequestNext);
            downloadThread.start();
        }
    }

    private Handler mTimerHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.e(TAG, "msg.arg1 : " + msg.arg1);
            if (msg.arg1 > 0) {
                if (downloadThread == null) {
                    Log.e(TAG, "downloadThread == null");
                    if (downloadPrepareThread != null) {
                        if (downloadPrepareThread.getState() == Thread.State.NEW) {
                            downloadPrepareThread.start();
                        }
                    }
                } else {
                    Log.e(TAG, "downloadThread != null");

                    try {
                        if (downloadThread.isComplete) {
                            Log.e(TAG, "downloadThread isComplete");
                            if (downloadPrepareThread != null) {
                                if (downloadPrepareThread.getState() == Thread.State.NEW) {
                                    downloadPrepareThread.start();
                                }
                            }
                        } else {
                            Log.e(TAG, "downloadThread is NOT Complete");
                            if (downloadPrepareThread != null) {
                                downloadThread.setCancel();
                            }

                            Message reMsg = new Message();
                            reMsg.what = msg.what;
                            reMsg.arg1 = --msg.arg1;
                            mTimerHandler.sendMessageDelayed(reMsg, 1000);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "downloadThread.isComplete : ", e);
                        if (downloadPrepareThread != null) {
                            downloadThread.setCancel();
                        }

                        Message reMsg = new Message();
                        reMsg.what = msg.what;
                        reMsg.arg1 = --msg.arg1;
                        mTimerHandler.sendMessageDelayed(reMsg, 1000);
                    }
                }
            }
        }
    };

    public void requestNextStreaming(CacheInfo info, int index) {
        Log.e(TAG, "requestNextStreaming");
        Log.e(TAG, "fileName : " + info.getFileName());
        Log.e(TAG, "path : " + info.getFilePath());
        Log.e(TAG, "index : " + index);
        Log.e(TAG, "playMode : " + info.getPlayMode());

        checkTotalAvailableMem(info.getPlayMode(), info.getPlayerType());

        prepare(false, info, index, false, false, true);
        callback.setProgressNextDownload(false);
    }

    private class DownloadThread extends Thread {
        private boolean isCancel = false;
        public boolean isComplete = false;
        private boolean init;
        private String filePath;
        private String fileInfo;
        private String fileName;
        private String path;
        private String playerType;
        private int index;
        private int totalDownloadedFileSize;
        private int headerOffset = 0;
        private boolean isBuffering;
        private boolean isLastmemory;
        private boolean isRequestNext;

        private URLConnection conn;
        private FileOutputStream fos;
        private InputStream is;

        private int playMode;

        private boolean isStart;

        DownloadThread(boolean init, String fileInfo, String path, int index, int playMode, String playerType, boolean isBuffering, boolean isLastmemory, boolean isRequestNext) {
            init();

            this.init = init;
            this.fileInfo = fileInfo;
            this.path = path;
            this.index = index;
            this.playMode = playMode;
            this.playerType = playerType;
            this.isBuffering = isBuffering;
            this.isLastmemory = isLastmemory;
            this.isRequestNext = isRequestNext;

            Log.e(TAG, "DownloadThread()");
            Log.e(TAG, "init : " + init);
            Log.e(TAG, "fileInfo : " + fileInfo);
            Log.e(TAG, "index : " + index);
            Log.e(TAG, "playMode : " + playMode);
            Log.e(TAG, "playerType : " + playerType);
            Log.e(TAG, "isBuffering : " + isBuffering);
            Log.e(TAG, "isLastmemory : " + isLastmemory);
        }

        @Override
        public void run() {
            Log.e(TAG, "isBuffering : " + isBuffering);
            int downloadSize = -1;

            if(Util.isNetworkStat(mContext)) {
                checkTotalAvailableMem(playMode, playerType);

                while (true) {
                    fileName = generateRandomFileName();
                    int count = dbManager.selectCount(null, fileName);

                    if (count == 0) {
                        break;
                    }
                }

                for (CacheFunctionParam param : functionHashMap.values()) {
                    if (playerType.equals(param.getPlayerType())) {
                        filePath = Environment.getExternalStorageDirectory() + "/" + param.getRootFolderName() + "/" + param.getFolderName() + "/" + fileName;

                        break;
                    }
                }

                Log.e(TAG, "filePath : " + filePath);

                File file = createFile(filePath);
//                File tmpFile = null;

                try {
                    fos = new FileOutputStream(file);
//                    if (playerType == PLAYER_TYPE_MELON) {
//                        tmpFile = createFile(tmpFilePath);
//                        tmpFos = new FileOutputStream(tmpFile);
//                    }
                } catch (Exception e) {
                    Log.e(TAG, "", e);
                }

                boolean isLowSize = false;

                if (playMode == CacheInfo.PLAY_MODE_DIVIDE_FILE) {
                    isLowSize = getTotalFileSize(path, true, isRequestNext);
                    Log.e(TAG, "isLowSize : " + isLowSize);
                }

                if (isTimeout) {
                    Log.e("SG2","소켓타임아웃이라 다운로드 안됨?");
                    isTimeout = false;
                    isComplete = true;
                    callback.onError();
                    return;
                }

                downloadSize = connect();
                boolean isException = false;
                boolean isUnderNougat = Build.VERSION.SDK_INT < 24;

                while (!isCancel) {
                    if (fos != null && is != null) {
                        try {
                            int len;
                            byte[] buff = new byte[1024];

                            if ((len = is.read(buff)) != -1) {
                                fos.write(buff, 0, len);
                                totalDownloadedFileSize += len;

                                //Under Nougat Version && 최소 512kb 다운받으면 실행?
                                if (isUnderNougat && !isStart && !isBuffering) {
                                    if (totalDownloadedFileSize >= STREAMING_BUFFER_MIN_START_SIZE) {
                                        Log.e(TAG, "###########start()");
                                        isStart = true;
                                        callback.onPrepare(init, filePath, index, playerType);
                                    }
                                }
                            } else {
                                Log.e(TAG, "download finish " + totalDownloadedFileSize + "bytes");
                                break;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "", e);
                            isException = true;
                            break;
                        }
                    } else {
                        break;
                    }
                }

                int totalIndex = callback.getFileTotalSize() / STREAMING_BUFFER_BLOCK_SIZE;
                if (callback.getFileTotalSize() % STREAMING_BUFFER_BLOCK_SIZE == 0) {
                    totalIndex -= 1;
                }

                if (isException) {
                    isCancel = true;
                    downloadSize = -1;
                } else {
                    if (index == totalIndex) {
                        //마지막 인덱스
                    } else {
                        //중간 인덱스
                        if ((downloadSize - totalDownloadedFileSize != 0)) {
                            downloadSize = -1;
                            isCancel = true;
                        }
                    }
                }

                disconnect();
                close();

                if (!isCancel) {
                    if (playMode == CacheInfo.PLAY_MODE_SINGLE_FILE) {
                        try {
                            PasswordCrypto.encrypt(SEED_KEY, file);
                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                        }
                    }

                    dbManager.insert(fileInfo, fileName, totalDownloadedFileSize, playerType);

                    if (isBuffering) {
                        Log.e(TAG, "%%%%%%%%%%%%%%start()");
                        callback.onBufferingFinish(filePath, index, playerType);
                    } else if (!isStart || isLowSize) {
                        callback.onPrepare(init, filePath, index, playerType);
                    }
                } else {
                    try {
                        file.delete();
                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                    }
                }
            } else {
                if (isBuffering) {
                    new Handler(mContext.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            callback.onCompleteCache();
                        }
                    }, 1000);

                    isComplete = true;
                    return;
                }
            }

            isComplete = true;

            if (downloadSize == -1 && playMode == CacheInfo.PLAY_MODE_DIVIDE_FILE) {
                callback.initRequestNextSection();
            }
        }

        public void setHeaderSize(int offset) {
            this.headerOffset = offset;
        }

        private void init() {
            isStart = false;
            conn = null;
            fos = null;
            is = null;
            isComplete = false;
        }

        private int connect() {
            Log.e(TAG, "connect() " + path);
            URL url = null;
            int maxCount = 3;
            int count = 0;

            try {
                url = new URL(path);
            } catch (Exception e) {
                Log.e(TAG, "", e);
                disconnect();
                isCancel = true;
                return -1;
            }

            int fromByte = 0;
            int toByte = 0;

            if (index == 0) {
                fromByte = 0;
            } else {
                fromByte = (index * STREAMING_BUFFER_BLOCK_SIZE) + 1;
            }
            fromByte += headerOffset;

            toByte = (index + 1) * STREAMING_BUFFER_BLOCK_SIZE;
            toByte += headerOffset;

            String value = "";

            if (playMode == CacheInfo.PLAY_MODE_SINGLE_FILE) {
                value = "bytes=0-";
            } else if (playMode == CacheInfo.PLAY_MODE_DIVIDE_FILE) {
                value = "bytes=" + fromByte + "-" + toByte;
            }

            Log.e(TAG, "value : " + value);

            while (count < maxCount) {
                try {
                    conn = url.openConnection();
                    conn.setConnectTimeout(10 * 1000);
                    conn.setReadTimeout(30 * 1000);
                    conn.setRequestProperty("Range", value);

                    is = conn.getInputStream();
                    return (toByte - fromByte + 1);
                } catch (Exception e) {
                    Log.e(TAG, "", e);
                    disconnect();
                    count++;
                }
            }

            if (!(count < maxCount)) {
                Log.e("SG2","캐시 플레이어 다운로드 데이터 불안정");
                isCancel = true;
            }

            return -1;
        }

        private void disconnect() {
            Log.e(TAG, "disconnect()");
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    Log.e(TAG, "", e);
                }
            }
        }

        private void close() {
            if (fos != null) {
                try {
                    fos.flush();
                    fos.close();
                } catch (Exception e) {
                    Log.e(TAG, "", e);
                }
            }
        }

        private File createFile(String filePath) {
            Log.e(TAG, "createFile() " + filePath);
            File file = new File(filePath);

            if (file.exists()) {
                try {
                    file.delete();
                } catch (Exception e) {
                    Log.e(TAG, "", e);
                }
            }

            try {
                file.createNewFile();
            } catch (Exception e) {
                Log.e(TAG, "", e);
            }

            return file;
        }

        public void setCancel() {
            Log.e(TAG, "setCancel()");
            isCancel = true;
        }
    }

    private boolean isTimeout = false;
    private boolean getTotalFileSize(String path, boolean isLowSize, boolean isRequestNext) {
        Log.e(TAG, "getFileTotalSize() " + " " + path + " " + isLowSize);
        isTimeout = false;
        int cachedTotalFileSize = -1;
        int cachedHeaderSize = 0;
        int totalFileSize = -1;
        int headerSize = 0;
        boolean isFileChanged = false;

        //	db 데이터 가져오기
        List<CacheDBFileSizeInfo> infos = dbManager.selectTotalFileSize(path);
        CacheDBFileSizeInfo info = null;

        if (infos.size() > 0) {
            info = infos.get(0);
        }

        if (info != null) {
            cachedTotalFileSize = info.getTotalFileSize();
            cachedHeaderSize = info.getHeaderSize();
            Log.e(TAG, "Cached Total File Size " + cachedTotalFileSize);
            Log.e(TAG, "Cached header Size " + cachedHeaderSize);
        }

        if (isRequestNext && cachedTotalFileSize != -1) {
            totalFileSize = cachedTotalFileSize;
            headerSize = cachedHeaderSize;
        } else {
            totalFileSize = getTotalFileSizeConn(path);
            headerSize = getHeaderSizeConn(path);

            if (info == null) {
                if (totalFileSize > 0) {
                    dbManager.insertTotalFileSize(path, totalFileSize, headerSize);
                }
            } else if (info != null) {
                if (totalFileSize != cachedTotalFileSize) {
                    isFileChanged = true;
                    if (totalFileSize > 0) {
                        dbManager.updateTotalFileSize(path, totalFileSize, headerSize);
                    }
                }
            }
        }

        if (totalFileSize == -1 || headerSize == -1) {
            isTimeout = true;
            return true;
        }

        Log.e(TAG, "totalFileSize : " + totalFileSize);
        Log.e(TAG, "headerSize : " + headerSize);

        //	callback
        callback.onInfoChanged(totalFileSize, headerSize);

        //	set headerSize
        if (downloadThread != null && !downloadThread.isComplete) {
            downloadThread.setHeaderSize(headerSize);
        }

        //	reture value
        if (isLowSize) {
            return totalFileSize < STREAMING_BUFFER_MIN_START_SIZE ? true : false;
        } else {
            Log.e(TAG, "isFileChanged : " + isFileChanged);
            return isFileChanged;
        }
    }

    private int getTotalFileSizeConn(String path) {
        int totalSize = -1;
        int maxCount = 3;
        int count = 0;

        URL url = null;
        URLConnection conn = null;

        try {
            url = new URL(path);
        } catch (Exception e) {
            Log.e(TAG, "", e);
            return totalSize;
        }

        Log.e(TAG, "getTotalFileSize : " + path);

        while (count < maxCount) {
            try {
                conn = url.openConnection();
                conn.setConnectTimeout(5 * 1000);
                conn.setReadTimeout(5 * 1000);
                totalSize = conn.getContentLength();
                Log.e(TAG, "totalSize : " + totalSize);
                break;
            } catch (Exception e) {
                Log.e(TAG, "", e);
                count++;
            }
        }

        return totalSize;
    }

    private int getHeaderSizeConn(String path) {
        int headerSize = 0;
        int maxCount = 3;
        int count = 0;

        InputStream is = null;
        URL url = null;
        URLConnection conn = null;

        try {
            url = new URL(path);
        } catch (Exception e) {
            Log.e(TAG, "", e);
            headerSize = -1;
            return headerSize;
        }

        Log.e(TAG, "getHeaderSize");

        while (count < maxCount) {
            try {
                conn = url.openConnection();
                conn.setConnectTimeout(5 * 1000);
                conn.setReadTimeout(5 * 1000);
                conn.setRequestProperty("Range", "bytes=0-10");

                is = conn.getInputStream();

                byte[] buff = new byte[10];
                is.read(buff);

                headerSize = getHeaderSize(buff);
                Log.e(TAG, "totalSize : " + headerSize);
                break;
            } catch (Exception e) {
                Log.e(TAG, "", e);
                headerSize = -1;
                count++;
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (Exception e) {
                        Log.e(TAG, "", e);
                    }
                }
            }
        }

        return headerSize;
    }

    private int getHeaderSize(byte[] buff) {
        Log.e(TAG, "getHeaderSize()");
        int headerSize = 0;

        try {
            String id3 = new String(buff, 0, 3);

            if (!id3.equals("ID3")) {
                return headerSize;
            }

            headerSize = (buff[9] & 0xFF) | ((buff[8] & 0xFF) << 7) | ((buff[7] & 0xFF) << 14) | ((buff[6] & 0xFF) << 21) + 10;
        } catch (Exception e) {
            Log.e(TAG, "", e);
            return 0;
        }

        return headerSize;
    }

    private String getEncryptFileName(String name) {
        String fileName = name;
        String encFileName = "";
        Log.e(TAG, "fileName " + fileName);

        try {
            encFileName = PasswordCrypto.encrypt(SEED_KEY, fileName);
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
        Log.e(TAG, "encFileName " + encFileName);
        return encFileName;
    }

    private CacheDBInfo getCacheInfo(String fileInfo) {
        List<CacheDBInfo> dbList = new ArrayList<CacheDBInfo>();
        dbList.addAll(dbManager.select(fileInfo, null));

        if (dbList.size() > 0) {
            CacheDBInfo domain = dbList.get(0);

            dbManager.updateDate(domain);
            return domain;
        } else {
            return null;
        }
    }

    private String generateRandomFileName() {
        String hex = "";
        int num = 21;

        for (int i=0; i<num; i++) {
            Random rand = new Random();
            int myRandom = rand.nextInt(0x1000000);
            String str = Integer.toHexString(myRandom);
            hex += str;
        }

        return hex;
    }

    public boolean checkTotalAvailableMem(int playerMode, String playerType) {
        Log.e(TAG, "checkTotalAvailableMem " + playerType);
        int limit_mem = 0;

        if (playerMode == CacheInfo.PLAY_MODE_SINGLE_FILE) {
            limit_mem = CACHE_MEM_MIN_LIMIT_SINGLE;
        } else {
            limit_mem = CACHE_MEM_MIN_LIMIT_DIVIDE;
        }

        if (hasAvailableLocalMem(limit_mem)) {
            if (!hasAvailableCacheMem(playerType)) {
                deleteCacheFile(playerType);
            }

            return true;
        } else {
            if (hasAvailableCachedMem(playerType)) {
                deleteCacheFile(playerType);
                return true;
            }
        }

        return false;
    }

    private long hasAvailableLocalMem() {
        String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        StatFs stat = new StatFs(dirPath);
        long available = stat.getAvailableBytes();

        return available;
    }

    private boolean hasAvailableLocalMem(int limit) {
        boolean hasAvailableMem = false;

        Log.e(TAG, "hasAvailableLocalMem " + limit);

        long available = hasAvailableLocalMem();
        Log.e(TAG, "Total Available " + available + "bytes");

        if (available > limit) {
            hasAvailableMem = true;
        }

        return hasAvailableMem;
    }

    public boolean hasAvailableCacheMem(String type) {
        Log.e(TAG, "hasAvailableCacheMem " + type);
        boolean hasAvailableMem = false;
        long size = 0;
        int limit = 0;
        String dirPath = Environment.getExternalStorageDirectory() + "";

        for (CacheFunctionParam param : functionHashMap.values()) {
            if (param.getPlayerType().equals(type)) {
                dirPath += "/" + param.getRootFolderName() + "/" + param.getFolderName() + "/";

                if (param.getPlayMode() == CacheInfo.PLAY_MODE_SINGLE_FILE) {
                    limit = CACHE_MEM_MAX_LIMIT_SINGLE;
                } else {
                    limit = CACHE_MEM_MAX_LIMIT_DIVIDE;
                }
                break;
            }
        }

        Log.e(TAG, "dirPath " + dirPath);
        File dir = new File(dirPath);

        size = getDirSize(dir);
        Log.e(TAG, "size : " + getFormmat(size));

        if (size < limit) {
            hasAvailableMem = true;
        }

        return hasAvailableMem;
    }

    public boolean hasAvailableCachedMem(String type) {
        Log.e(TAG, "hasAvailableCachedMem " + type);
        boolean hasAvailableMem = false;
        long size = 0;
        int limit = 0;
        String dirPath = Environment.getExternalStorageDirectory() + "";

        for (CacheFunctionParam param : functionHashMap.values()) {
            if (param.getPlayerType().equals(type)) {
                dirPath += "/" + param.getRootFolderName() + "/" + param.getFolderName() + "/";

                if (param.getPlayMode() == CacheInfo.PLAY_MODE_SINGLE_FILE) {
                    limit = CACHE_MEM_MIN_LIMIT_SINGLE;
                } else {
                    limit = CACHE_MEM_MIN_LIMIT_DIVIDE;
                }
                break;
            }
        }

        Log.e(TAG, "dirPath " + dirPath);
        File dir = new File(dirPath);

        size = getDirSize(dir);
        Log.e(TAG, "size : " + size);

        if (size > limit) {
            hasAvailableMem = true;
        }

        return hasAvailableMem;
    }

    private long getDirSize(File dir) {
        long size = 0;

        File[] files = dir.listFiles();

        if (files == null) {
            return size;
        }

        for (File file : files) {
            if (file.isFile()) {
                size += file.length();
            } else {
                size += getDirSize(file);
            }
        }

        return size;
    }

    private String getFormmat(long dataSize) {
        String str = String.valueOf(dataSize);
        String value = "";
        String head = "";
        String body = "";

        int length = str.length();

        if (length > 3) {
            int headSize = length%3;
            head = str.substring(0, headSize);
            body = str.substring(headSize, str.length());
        } else {
            return str;
        }

        char[] ch = body.toCharArray();

        value += head;

        if (head.length() > 0) {
            value += ",";
        }

        for (int i=0; i<body.length(); i++) {
            if ((i/3 > 0) && (i%3 == 0)) {
                value += ",";
            }

            value += ch[i];
        }

        return value;
    }

    public void deleteCacheFile(String type) {
        Log.e(TAG, "deleteCacheFile start " + type);

        long dir_size = 0;
        int limit_size = 0;

        List<CacheDBInfo> list = new ArrayList<CacheDBInfo>();
        String dirPath = Environment.getExternalStorageDirectory() + "";

        for (CacheFunctionParam param : functionHashMap.values()) {
            if (param.getPlayerType().equals(type)) {
                dirPath += "/" + param.getRootFolderName() + "/" + param.getFolderName() + "/";

                if (param.getPlayMode() == CacheInfo.PLAY_MODE_SINGLE_FILE) {
                    limit_size = CACHE_MEM_MAX_LIMIT_SINGLE - CACHE_MEM_MIN_LIMIT_SINGLE;
                } else {
                    limit_size = CACHE_MEM_MAX_LIMIT_DIVIDE - CACHE_MEM_MIN_LIMIT_DIVIDE;
                }
                break;
            }
        }

        File dir = new File(dirPath);
        dir_size = getDirSize(dir);

        Log.e(TAG, "limit_size : " + limit_size);
        Log.e(TAG, "dir_size : " + dir_size);
        Log.e(TAG, "dirPath : " + dirPath);

        list = dbManager.select(null, null);

        if (list != null) {
            Log.e(TAG, "list.size : " + list.size());
        }

        for (CacheDBInfo domain : list) {
            if (domain.getType() == type) {
                int fileSize = domain.getFileSize();
                String fileName = domain.getFileName();

                Log.e(TAG, "fileName " + fileName);
                Log.e(TAG, "fileSize " + fileSize);

                File file = new File(dirPath + fileName);

                if (file.exists()) {
                    try {
                        file.delete();
                        dir_size -= fileSize;
                        Log.e(TAG, "dir_size : " + dir_size);
                        dbManager.delete(fileName);
                    } catch (Exception e) {
                        Log.e(TAG, "", e);
                    }
                } else {
                    Log.e(TAG, "file not EXISTS");
                    dbManager.delete(fileName);
                }
            }

            if (dir_size <= limit_size) {
                break;
            }
        }
        Log.e(TAG, "deleteCacheFile end " + type);
    }

    public void deleteCacheFile() {
        Log.e(TAG, "deleteCacheFile()");
        if (functionHashMap == null) {
            return;
        }

        for (CacheFunctionParam param : functionHashMap.values()) {
            String dirPath = Environment.getExternalStorageDirectory() + "/" + param.getRootFolderName() + "/" + param.getFolderName() + "/";
            File dir = new File(dirPath);

            try {
                if (dir != null && dir.exists()) {
                    Log.e(TAG, "deleteCacheFile() dir.exists()");
                    for (File file : dir.listFiles()) {
                        if (file != null) {
                            file.delete();
                            Log.e(TAG, "file.delete()");
                        }
                    }
                }
            }catch (Exception e){

            }
        }

        try {
            dbManager.deleteAll();
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
    }

    private void createCacheDir() {
        if (functionHashMap == null) {
            return;
        }

        for (CacheFunctionParam p : functionHashMap.values()) {
            String dirPath = Environment.getExternalStorageDirectory() + "/" + p.getRootFolderName() + "/" + p.getFolderName() + "/";

            File file = new File(dirPath);

            if (!file.exists()) {
                try {
                    file.mkdirs();
                } catch (Exception e) {
                    Log.e(TAG, "", e);
                }
            }
        }
    }

    class CleanupCacheFileThread extends Thread {
        @Override
        public void run() {
            Log.e(TAG, "CleanupCacheFileThread");
            List<CacheDBInfo> cacheList = new ArrayList<CacheDBInfo>();
            cacheList.addAll(dbManager.select(null, null));

            HashMap<String, List<CacheFileData>> cacheFileHash = new HashMap<>();

            for (CacheFunctionParam param : functionHashMap.values()) {
                String dirPath = Environment.getExternalStorageDirectory() + "/" + param.getRootFolderName() + "/" + param.getFolderName() + "/";
                List<CacheFileData> cacheFileData = getFileList(dirPath);

                cacheFileHash.put("/" + param.getRootFolderName() + "/" + param.getFolderName() + "/", cacheFileData);
            }

            if (cacheList.size() > 0) {
                for (CacheDBInfo cache : cacheList) {
                    boolean isExists = false;
                    String fileName = cache.getFileName();

                    List<CacheFileData> list = cacheFileHash.get(cache.getType());

                    if (list.size() > 0) {
                        for (CacheFileData data : list) {
                            if (fileName.equals(data.getFileName())) {
                                isExists = true;
                            } else {
                                data.count();
                            }
                        }
                    }

                    if (!isExists) {
                        dbManager.delete(fileName);
                    }
                }

                for (String key : cacheFileHash.keySet()) {
                    for (CacheFileData data : cacheFileHash.get(key)) {
                        String fileName = data.getFileName();
                        int count = data.getMissCount();
                        String filePath = Environment.getExternalStorageDirectory() + key + fileName;

                        if (cacheList.size() == count) {
                            deleteFile(filePath);
                        } else {
                            File file = new File(filePath);

                            if (file.exists()) {
                                if (file.length() < CACHE_MIN_FILE_SIZE) {
                                    deleteFile(filePath);
                                }
                            }
                        }
                    }
                }
            } else {
                deleteCacheFile();
            }
        }
    }

    private class CacheFileData {
        private String fileName;
        private int missCount = 0;

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public int getMissCount() {
            return missCount;
        }

        public void setMissCount(int missCount) {
            this.missCount = missCount;
        }

        public void count() {
            this.missCount += 1;
        }
    }

    private List<CacheFileData> getFileList(String dirPath) {
        List<CacheFileData> fileList = new ArrayList<>();
        File file = new File(dirPath);

        try {
            if (file != null && file.exists()) {
                for (String fileName : file.list()) {
                    CacheFileData data = new CacheFileData();
                    data.setFileName(fileName);
                    fileList.add(data);
                }
            }
        }catch (NullPointerException e){
        }

        return fileList;
    }

    public void deleteFile(String filePath) {
        Log.e(TAG, "deleteFile " + filePath);
        File file = new File(filePath);

        if (file.exists()) {
            try {
                file.delete();
            } catch (Exception e) {
                Log.e(TAG, "", e);
            }
        }
    }
}
