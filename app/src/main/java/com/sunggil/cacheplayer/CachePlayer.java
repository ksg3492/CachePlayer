package com.sunggil.cacheplayer;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.sunggil.cacheplayer.model.CacheFunctionParam;
import com.sunggil.cacheplayer.model.CacheInfo;
import com.sunggil.cacheplayer.callback.CacheManagerCallback;
import com.sunggil.cacheplayer.callback.CachePlayerCallback;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class CachePlayer {
    private final String TAG = "CachePlayer";

    //must require
    private Context mContext = null;
    private String mPackageName = "";
    private int mPackageUid = -1;

    //manager
    private CacheManager cacheManager = null;

    private CacheInfo cacheInfo = new CacheInfo();

    private CachePlayerCallback mPlayerCallback = null;

    //player values
    private boolean mIsComplete = true;

    private int mFileTotalSize = -1;

    private int mCurrentPosition = -1;
    private int mDuration = -1;
    private boolean mIsTimeCheck = false;
    private boolean mIsGetDuration = false;

    private int mCurrentIndex = -1;
    private int mTotalIndex = -1;
    private int mFile_section = -1;
    private int mFile_request_section = -1;

    private boolean mIsNeedNextSection = false;
    private boolean mIsRequestNextSection = false;
    private boolean mIsRetryNextSection = false;
    private boolean mIsProgressNextDownload = false;

    private int mPlayThread = -1;
    private final int PLAY_THREAD_0 = 0;
    private final int PLAY_THREAD_1 = 1;

    private PlayMusicThread mThread0 = null;
    private PlayMusicThread mThread1 = null;

    private final int STREAMING_BUFFER_BLOCK = 3 * 1024 * 1024;
    private final int STREAMING_BUFFER_REQUEST_BLOCK = 2 * 1024 * 1024;

    public static final String CACHE_FUNCTIONS = "cache_functions";
    public static final String CACHE_FOLDER_ROOT_NAME = "cache_folder_root_name";
    public static final String CACHE_FOLDER_FUNCTION_NAME = "cache_folder_function_name";

    public CachePlayer(Context context, String authKey, String packageName, CachePlayerCallback callback, HashMap<String, CacheFunctionParam> param) {
        this.mContext = context;
        this.mPackageName = packageName;
        this.mPlayerCallback = callback;
        printNetworkUsage(context);

        //sample
        param = new HashMap<>();

        CacheFunctionParam cfp1 = new CacheFunctionParam("Melon","T2C3/cache", ".melon", CacheInfo.PLAY_MODE_SINGLE_FILE);
        CacheFunctionParam cfp2 = new CacheFunctionParam("News","T2C3/cache",".news", CacheInfo.PLAY_MODE_SINGLE_FILE);
        CacheFunctionParam cfp3 = new CacheFunctionParam("Podbbang","T2C3/cache",".podbbang", CacheInfo.PLAY_MODE_DIVIDE_FILE);
        param.put(cfp1.getPlayerType(), cfp1);
        param.put(cfp2.getPlayerType(), cfp2);
        param.put(cfp3.getPlayerType(), cfp3);
        //sample

        if (cacheManager == null) {
            cacheManager = new CacheManager(mContext, authKey, managerCallback, param);
        }
        init();
    }

    private void init() {
        Log.e(TAG, "init()");
        cacheInfo.init();

        mPlayThread = -1;
        mThread0 = null;
        mThread1 = null;

        mCurrentIndex = -1;
        mTotalIndex = -1;

        mFileTotalSize = -1;

        mCurrentPosition = -1;
        mDuration = -1;
        mIsTimeCheck = false;
        mIsGetDuration = false;

        mFile_section = -1;
        mFile_request_section = -1;

        mIsComplete = true;

        mIsNeedNextSection = false;
    }

    public void playMusic(boolean isLocalFile, String fileName, String path, String playerType, int playMode, int index, boolean isLastmemory, int progress) {
        stop();
        init();

        mRunnable.setParams(isLocalFile, fileName, path, playerType, playMode, index, isLastmemory, progress);

        Message msg = new Message();
        msg.what = 0;
        msg.arg1 = 5;
        mTimerHandler.removeMessages(0);
        mTimerHandler.sendMessage(msg);
    }

    private interface TimerRunnable extends Runnable {
        void setParams(boolean isLocalFile, String fileName, String path, String playerType, int playMode, int index, boolean isLastmemory, int progress);
    }

    private TimerRunnable mRunnable = new TimerRunnable() {
        boolean mIsLocalFile;
        String mFileName;
        String mPath;
        String mPlayerType;
        int mPlayMode;
        int mIndex;
        boolean mIsLastmemory;
        int progress;

        @Override
        public void setParams(boolean isLocalFile, String fileName, String path, String playerType, int playMode, int index, boolean isLastmemory, int progress) {
            this.mIsLocalFile = isLocalFile;
            this.mFileName = fileName;
            this.mPath = path;
            this.mPlayerType = playerType;
            this.mPlayMode = playMode;
            this.mIndex = index;
            this.mIsLastmemory = isLastmemory;
            this.progress = progress;
        }

        @Override
        public void run() {
            if (progress != -1) {
                mCurrentPosition = progress;
            }

            Log.e("SG2","redirect start");
            if (!mIsLocalFile) {
                this.mPath = getFinalURL(this.mPath);
            }
            Log.e("SG2","redirect end");

            cacheInfo.setLocalFile(this.mIsLocalFile);
            cacheInfo.setFileName(this.mFileName);
            cacheInfo.setFilePath(this.mPath);
            cacheInfo.setPlayMode(this.mPlayMode);
            cacheInfo.setPlayerType(this.mPlayerType);
            cacheInfo.setIndex(this.mIndex);
            cacheInfo.setLastMemory(this.mIsLastmemory);

            Log.e(TAG, "fileName " + mFileName);
            Log.e(TAG, "path " + mPath);
            Log.e(TAG, "playMode " + mPlayMode);
            Log.e(TAG, "currentIndex " + mIndex);
            Log.e(TAG, "isLastmemory " + mIsLastmemory);
            Log.e(TAG, "isLocalFile " + mIsLocalFile);

            if (mIsLocalFile) {
                managerCallback.onPrepare(true, cacheInfo.getFilePath(), cacheInfo.getIndex(), cacheInfo.getPlayerType());
            } else {
                cacheManager.prepare(true, cacheInfo, mIndex, false, mIsLastmemory, false);
            }
        }
    };

    private String getFinalURL(String path) {
        try {
            URL url = new URL(path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "");
            conn.connect();
            int resCode = conn.getResponseCode();

            if (resCode == HttpURLConnection.HTTP_SEE_OTHER || resCode == HttpURLConnection.HTTP_MOVED_PERM || resCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                String location = conn.getHeaderField("Location");
                if (location.startsWith("/")) {
                    location = url.getProtocol() + "://" + url.getHost() + location;
                }
                return getFinalURL(location);
            }
        } catch (RuntimeException e) {
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }

        return path;
    }

    private boolean isFinalURL(String path) {
        try {
            int index = 0;
            String fileName = "";

            index = path.lastIndexOf("/");
            fileName = path.substring(index, path.length());

            index = fileName.lastIndexOf(".");
            return (index != -1) ? true:false;
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }

        return false;
    }

    private Handler mTimerHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.arg1 > 0) {
                if (mIsComplete) {
                    new Thread(mRunnable).start();
                } else {
                    Message reMsg = new Message();
                    reMsg.what = msg.what;
                    reMsg.arg1 = --msg.arg1;
                    mTimerHandler.sendMessageDelayed(reMsg, 1000);
                }
            }
        }
    };

    public void destroy() {
        Log.e(TAG, "destroy()");
        if (mThread0 != null) {
            mThread0.stopMusic();
        }
        if (mThread1 != null) {
            mThread1.stopMusic();
        }
    }

    public void closeDB() {
        if (cacheManager != null) {
            cacheManager.closeDB();
        }
    }

    public boolean isPlaying() {
        Log.e(TAG, "isPlaying()");
        boolean playing = false;

        Log.e(TAG, "mPlayThread : " + mPlayThread);
        if (mPlayThread == PLAY_THREAD_0) {
            if (mThread0 != null) {
                Log.e(TAG, "thread0 != null");
                playing = mThread0.isPlaying();
            }
        } else if (mPlayThread == PLAY_THREAD_0) {
            if (mThread1 != null) {
                Log.e(TAG, "thread1 != null");
                playing = mThread1.isPlaying();
            }
        }

        Log.e(TAG, "playing : " + playing);
        return playing;
    }

    public void play() {
        if (mPlayThread == PLAY_THREAD_0) {
            if (mThread0 != null) {
                mThread0.playMusic();
            }
        } else if (mPlayThread == PLAY_THREAD_1) {
            if (mThread1 != null) {
                mThread1.playMusic();
            }
        }

        mPlayerCallback.onPlayCache(mCurrentPosition);
    }

    public void pause() {
        if (mPlayThread == PLAY_THREAD_0) {
            if (mThread0 != null) {
                mThread0.pauseMusic();
            }
        } else if (mPlayThread == PLAY_THREAD_1) {
            if (mThread1 != null) {
                mThread1.pauseMusic();
            }
        }

        mPlayerCallback.onPauseCache(mCurrentPosition);
    }

    public void stop() {
        Log.e(TAG, "stop()");
        pause();
        destroy();
        cancelProgressTimer();
    }

    public void seekTo(int progress) {
        if (mPlayThread == PLAY_THREAD_0) {
            if (mThread0 != null) {
                mThread0.seekTo(progress);
            }
        } else if (mPlayThread == PLAY_THREAD_1) {
            if (mThread1 != null) {
                mThread1.seekTo(progress);
            }
        }
    }

    public void progressTo(long progress) {
        Log.e(TAG, "progress : " + progress);
        mCurrentPosition = (int) progress;

        mPlayerCallback.onSeekToCache(mCurrentPosition);
    }

    private void onComplete() {
        Log.e(TAG, "onComplete()");
        pause();
        mIsComplete = true;
    }

    public synchronized boolean isBuffering(int progress) {
        Log.e(TAG, "isBuffering " + progress);
        if (cacheInfo.isLocalFile()) {
            return false;
        }

        int seekIndex = progress / mFile_section;
        Log.e(TAG, "seekIndex " + seekIndex + " currentIndex " + mCurrentIndex);

        if (seekIndex != mCurrentIndex) {
            destroy();
            mCurrentIndex = seekIndex;

            if (mIsProgressNextDownload) {
                Handler handler = new Handler(mContext.getMainLooper());
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        cacheManager.prepare(true, cacheInfo, mCurrentIndex, true, false, true);
                    }
                }, 300);
            } else {
                cacheManager.prepare(true, cacheInfo, mCurrentIndex, true, false, true);
            }

            return true;
        }

        return false;
    }

    /**********
     * Timer
     **********/

    private Timer mTimer = null;
    private TimerTask timerTask = null;

    private void startProgressTimer() {
        Log.e(TAG, "startProgressTimer()");
        try {
            mIsTimeCheck = true;

            if (mTimer == null) {
                if (mCurrentPosition == -1) {
                    mCurrentPosition = 0;
                }
                mTimer = new Timer();
                timerTask = new TimerTask() {

                    @Override
                    public void run() {
                        if (mIsTimeCheck) {
                            mCurrentPosition += 1;
                            mPlayerCallback.onUpdateCache(mCurrentPosition);

                            if (cacheInfo.getPlayMode() == CacheInfo.PLAY_MODE_DIVIDE_FILE) {
                                int request_position = (mCurrentIndex * mFile_section) + mFile_request_section;
                                int endPosition = (mCurrentIndex * mFile_section) + mFile_section;
                                Log.e(TAG, "[" + mCurrentIndex + "] " + request_position + "/" + mCurrentPosition + "/" + endPosition);

                                if (!mIsRequestNextSection && mCurrentPosition >= request_position) {
                                    mIsRequestNextSection = true;
                                    int index = mCurrentIndex + 1;
                                    Log.e(TAG, "[index] : " + index + " / " + mTotalIndex);

                                    if (index <= mTotalIndex) {
                                        if (mIsRetryNextSection && !Util.isNetworkStat(mContext)) {
                                            mIsRequestNextSection = false;
                                            return;
                                        }
                                        managerCallback.setProgressNextDownload(true);
                                        cacheManager.requestNextStreaming(cacheInfo, index);
                                    }
                                }
                            }
                        }
                    }
                };

                mTimer.schedule(timerTask, 1000, 1000);
            }
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
    }

    private void cancelProgressTimer() {
        Log.e(TAG, "cancelProgressTimer()");
        try {
            if (mTimer != null) {
                mCurrentPosition = -1;
                mTimer.cancel();
                mTimer = null;
                timerTask = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
    }
    
    /**********
     * Player Thread  &  Callback
     **********/

    private class PlayMusicThread extends Thread {
        private boolean isPlaying;
        private boolean isForceStop;

        private MediaExtractor mExtractor = null;
        private AudioTrack mAudioTrack = null;
        private MediaCodec mCodec = null;

        FileInputStream fis = null;
        FileDescriptor fd = null;


        int mInputBufIndex = -1;
        MediaFormat format = null;
        MediaCodec.BufferInfo info;

        final long kTimeOutUs = 10 * 1000;
        boolean sawInputEOS = false;

        String playerType;
        int index = 0;

        PlayMusicThread(String playerType, int index) {
            initThread();
            this.playerType = playerType;
            this.index = index;
        }

        @Override
        public void run() {
            Log.e(TAG, "PlayMusicThread run()");
            mCurrentIndex = index;
            mPlayerCallback.onLoadingWebView();

            mIsComplete = false;
            startProgressTimer();
            Log.e(TAG, "sawInputEOS : " + sawInputEOS);
            Log.e(TAG, "isForceStop : " + isForceStop);
            Log.e(TAG, "mIsComplete : " + mIsComplete);

            info = new MediaCodec.BufferInfo();

            while (!sawInputEOS && !isForceStop && !mIsComplete) {
                try {
                    if (!isPlaying) {
                        continue;
                    }

                    if (mAudioTrack != null) {
                        if (mAudioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                            continue;
                        }
                    }

                    mInputBufIndex = mCodec.dequeueInputBuffer(kTimeOutUs);

                    if (mInputBufIndex >= 0) {
                        ByteBuffer dstBuf = mCodec.getInputBuffer(mInputBufIndex);

                        int sampleSize = mExtractor.readSampleData(dstBuf, 0);

                        long presentationTimeUs = 0;

                        if (sampleSize < 0) {
                            Log.e(TAG, "saw input EOS");
                            sawInputEOS = true;
                            sampleSize = 0;
                        } else {
                            presentationTimeUs = mExtractor.getSampleTime();
                        }

                        mCodec.queueInputBuffer(mInputBufIndex, 0, sampleSize, presentationTimeUs, sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                        if (!sawInputEOS) {
                            mExtractor.advance();
                        }
                    } else {
                        Log.e(TAG, "inputBufIndex " + mInputBufIndex);
                    }

                    int mOutputBufIndex = mCodec.dequeueOutputBuffer(info, kTimeOutUs);

                    if (mOutputBufIndex >= 0) {
                        ByteBuffer buf = mCodec.getOutputBuffer(mOutputBufIndex);

                        final byte[] chunk = new byte[info.size];
                        buf.get(chunk);
                        buf.clear();
                        if (chunk.length > 0) {
                            mAudioTrack.write(chunk, 0, chunk.length);
                        }

                        mCodec.releaseOutputBuffer(mOutputBufIndex, false);
                    } else if (mOutputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        Log.e(TAG, "MediaCodec.INFO_OUTPUT_FORMAT_CHANGED");
                        format = mCodec.getOutputFormat();

                        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        int channelOut = 0;

                        if (channelCount == 1) {
                            channelOut = AudioFormat.CHANNEL_OUT_MONO;
                        } else {
                            channelOut = AudioFormat.CHANNEL_OUT_STEREO;
                        }

                        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC
                                , sampleRate
                                , channelOut
                                , AudioFormat.ENCODING_PCM_16BIT
                                , AudioTrack.getMinBufferSize(sampleRate, channelOut, AudioFormat.ENCODING_PCM_16BIT)
                                , AudioTrack.MODE_STREAM);

                        mAudioTrack.play();
                    } else {
                        Log.e(TAG, "dequeueOutputBuffer returned");
                    }
                } catch (RuntimeException e) {
                } catch (Exception e) {
                    Log.e(TAG, "", e);
                    isForceStop = true;
                    break;
                }
            }

            int reason = CachePlayerListener.PLAYER_COMPLETE_END_STREAM;

            if (isForceStop) {
                reason = CachePlayerListener.PLAYER_COMPLETE_FORCE_STOP;
            }

            playerListener.onComplete(reason);
        }

        private void initThread() {
            Log.e(TAG, "thread init()");
            isPlaying = false;
            isForceStop = false;

            index = 0;

            if (fis != null) {
                try {
                    fis.close();
                    fis = null;
                } catch (Exception e) {
                    Log.e(TAG, "", e);
                }
            }
            if (fd != null) {
                try {
                    fd = null;
                } catch (Exception e) {
                    Log.e(TAG, "", e);
                }
            }
            if (mExtractor != null) {
                try {
                    mExtractor.release();
                    mExtractor = null;
                } catch (Exception e) {
                    Log.e(TAG, "", e);
                }
            }
            if (mCodec != null) {
                try {
                    mCodec.stop();
                    mCodec.release();
                    mCodec.flush();
                    mCodec = null;
                } catch (Exception e) {
                    Log.e(TAG, "", e);
                }
            }
            if (mAudioTrack != null) {
                try {
                    mAudioTrack.stop();
                    mAudioTrack.release();
                    mAudioTrack.flush();
                    mAudioTrack = null;
                } catch (Exception e) {
                    Log.e(TAG, "", e);
                }
            }
        }

        public void setDataSource(String path) {
            Log.e(TAG, "setDataSource " + path);
            generateFD(path);
            initPlayer(path);
        }

        void playMusic() {
            Log.e(TAG, "playMusic()");
            isPlaying = true;
            mIsTimeCheck = true;
        }

        void pauseMusic() {
            Log.e(TAG, "pauseMusic()");
            isPlaying = false;
            mIsTimeCheck = false;
        }

        void stopMusic() {
            Log.e(TAG, "stopMusic()");
            isPlaying = false;
            isForceStop = true;
        }

        void seekTo(long progress) {
            Log.e(TAG, "seekTo " + progress);
            if (progress < 0) {
                progress = 0;
            }

            if (mExtractor != null) {
                mCurrentPosition = (int) progress;
                Log.e(TAG, "currentPosition " + mCurrentPosition);
                Log.e(TAG, "file_section " + mFile_section);

                long duration = 0;

                if (cacheInfo.isLocalFile()) {
                    duration = mCurrentPosition;
                } else if (cacheInfo.getPlayMode() == CacheInfo.PLAY_MODE_DIVIDE_FILE) {
                    duration = mCurrentPosition % mFile_section;
                }

                if (cacheInfo.getPlayMode() == CacheInfo.PLAY_MODE_SINGLE_FILE) {
                    if (duration == 0) {
                        duration = progress;
                    }
                }
                Log.e(TAG, "duration " + duration);

                duration = duration * 1000 * 1000;
                mExtractor.seekTo(duration, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                progressTo(progress);
            }
        }

        boolean isPlaying() {
            return isPlaying;
        }

        private void generateFD(String filePath) {
            Log.e(TAG, "generateFD() " + filePath);
            File file = new File(filePath);

            try {
                fis = new FileInputStream(file);
            } catch (Exception e) {
                Log.e(TAG, "", e);
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (Exception e1) {
                        Log.e(TAG, "", e1);
                    }
                }
            }

            try {
                if (fis != null) {
                    fd = fis.getFD();
                }
            } catch (Exception e) {
                Log.e(TAG, "", e);
            }
        }

        public void initPlayer(String path) {
            Log.e(TAG, "initPlayer()");
            mExtractor = new MediaExtractor();

            try {
                mExtractor.setDataSource(fd);
            } catch (Exception e) {
                CachePlayer.this.stop();
                mPlayerCallback.onPlayViaMediaPlayer(path);

                Log.e(TAG, "", e);

                return;
            }

            MediaFormat format = null;

            try {
                int trackCount = mExtractor.getTrackCount();
                Log.e(TAG, "trackCount : " + trackCount);
                format = mExtractor.getTrackFormat(0);
            } catch (Exception e) {
                Log.e(TAG, "", e);
                return;
            }

            String mime = format.getString(MediaFormat.KEY_MIME);

            boolean isException = false;
            try {
                mDuration = (int) format.getLong(MediaFormat.KEY_DURATION);
                mDuration = mDuration / 1000;
                if (mDuration <= 0) {
                    isException = true;
                }
                Log.e(TAG, "getDuration(1)");
            } catch (Exception e) {
                Log.e(TAG, "", e);
                isException = true;
            }

            if (isException) {
                Log.e(TAG, "fileTotalSize : " + mFileTotalSize);
                Log.e(TAG, "isLocalFile : " + cacheInfo.isLocalFile());
                if (cacheInfo.isLocalFile()) {
                    if (mFileTotalSize == -1) {
                        File file = new File(cacheInfo.getFileName());
                        if (file.exists()) {
                            Log.e(TAG, "file.exists()");
                            mFileTotalSize = (int) file.length();
                        } else {
                            Log.e(TAG, "file not exists");
                        }
                    }
                }

                if (mFileTotalSize != -1 && format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    Log.e(TAG, "getDuration(2)");
                    int bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                    mDuration = (int) getDuration(bitrate, mFileTotalSize);
                } else if (mFileTotalSize != -1 && format.containsKey("bit-rate")) {
                    Log.e(TAG, "getDuration(2-1)");
                    int bitrate = format.getInteger("bit-rate");
                    mDuration = (int) getDuration(bitrate, mFileTotalSize);
                } else {
                    Log.e(TAG, "getDuration(3)");
                    getDuration(path);

                    while (true) {
                        if (mIsGetDuration) {
                            mIsGetDuration = false;
                            break;
                        }

                        try {
                            Thread.sleep(100);
                        } catch (Exception e) {
                            Log.e("SG2","Exception : ", e);
                        }
                    }
                }
            }

            generateFileSection();

            try {
                mCodec = MediaCodec.createDecoderByType(mime);
            } catch (Exception e) {
                Log.e(TAG, "", e);
                return;
            }

            mCodec.configure(format, null, null, 0);
            mCodec.start();

            mExtractor.selectTrack(0);
        }
    }

    private long getDuration(int bitrate, int fileSize) {
        long duration = -1;

        if (fileSize == -1 || bitrate == 0) {
            return duration;
        }
        try {
            Log.e(TAG, "bitrate : " + bitrate);
            Log.e(TAG, "fileSize : " + fileSize);
            duration = ((long)(fileSize * 8L) / bitrate) * 1000;
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }

        return duration;
    }

    private void getDuration(String path) {
        MediaPlayer mp = new MediaPlayer();

        try {
            mp.setDataSource(path);
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    Log.e(TAG, "getDuration onPrepared()");
                    mDuration = mp.getDuration();
                    try {
                        mp.stop();
                        mp.release();
                    } catch (Exception e) {
                        Log.e(TAG, "", e);
                    }
                    mIsGetDuration = true;
                }
            });
            mp.prepare();
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
    }

    private void generateFileSection() {
        Log.e(TAG, "generateFileSction()");
        if (mFileTotalSize != -1) {
            try {
                int perseconds = mFileTotalSize / ((int) mDuration / 1000);
                Log.e(TAG, "perseconds " + perseconds);

                mFile_section = STREAMING_BUFFER_BLOCK / perseconds;
                mFile_request_section = STREAMING_BUFFER_REQUEST_BLOCK / perseconds;
                Log.e(TAG, "file_section " + mFile_section);
                Log.e(TAG, "file_request_section " + mFile_request_section);
            } catch (Exception e) {
                mFile_section = -1;
                mFile_request_section = -1;
                Log.e(TAG, "", e);
            }
        }
    }

    interface CachePlayerListener {
        int PLAYER_COMPLETE_END_STREAM = 0;
        int PLAYER_COMPLETE_NEXT = 1;
        int PLAYER_COMPLETE_FORCE_STOP = 2;
        int PLAYER_COMPLETE_SEEK = 3;

        void onComplete(int reason);
    }
    
    CachePlayerListener playerListener = new CachePlayerListener() {
        @Override
        public void onComplete(int reason) {
            Log.e(TAG, "playerListener " + reason);

            int playMode = cacheInfo.getPlayMode();

            if (reason == PLAYER_COMPLETE_END_STREAM) {
                Log.e(TAG, "playMode " + playMode);
                if (playMode == CacheInfo.PLAY_MODE_SINGLE_FILE) {
                    mIsComplete = true;
                    mFileTotalSize = -1;
                    mPlayerCallback.onCompleteCache();
                } else if (playMode == CacheInfo.PLAY_MODE_DIVIDE_FILE) {
                    Log.e(TAG, "currentIndex : " + mCurrentIndex);
                    Log.e(TAG, "totalIndex : " + mTotalIndex);

                    if (mCurrentIndex <= mTotalIndex) {
                        mIsRequestNextSection = false;
                        Log.e(TAG, "0000");
                        if (mPlayThread == PLAY_THREAD_0) {
                            if (mThread1 != null) {
                                Log.e(TAG, "thread1 != null");
                                mThread1.start();
                                mThread1.playMusic();
                                mPlayThread = PLAY_THREAD_1;

                                mThread0.stopMusic();
                                mThread0 = null;
                            } else {
                                Log.e(TAG, "thread1 == null");
                                init();
                                mPlayerCallback.onCompleteCache();
                            }
                        } else if (mPlayThread == PLAY_THREAD_1) {
                            if (mThread0 != null) {
                                Log.e(TAG, "thread0 != null");
                                mThread0.start();
                                mThread0.playMusic();
                                mPlayThread = PLAY_THREAD_0;

                                mThread1.stopMusic();
                                mThread1 = null;
                            } else {
                                Log.e(TAG, "thread0 == null");
                                init();
                                mPlayerCallback.onCompleteCache();
                            }
                        }
                    } else {
                        mIsComplete = true;
                        mFileTotalSize = -1;
                        mPlayerCallback.onCompleteCache();
                    }
                }
            } else if (reason == PLAYER_COMPLETE_NEXT) {
                mIsComplete = true;
                mFileTotalSize = -1;

                if (playMode == CacheInfo.PLAY_MODE_DIVIDE_FILE) {
                    mIsRequestNextSection = false;
                    mIsRetryNextSection = false;
                }

                mPlayerCallback.onCompleteCache();
            } else if (reason == PLAYER_COMPLETE_FORCE_STOP) {
                mIsComplete = true;
                mFileTotalSize = -1;

                if (playMode == CacheInfo.PLAY_MODE_DIVIDE_FILE) {
                    mIsRequestNextSection = false;
                    mIsRetryNextSection = false;
                }
            }
        }
    };

    CacheManagerCallback managerCallback = new CacheManagerCallback() {

        private boolean isChangedLastMemory = false;

        @Override
        public void onPrepare(boolean init, String url, int index, String playerType) {
            Log.e(TAG, "onPrepare " + init);

            if (isChangedLastMemory) {
                isChangedLastMemory = false;
                cacheInfo.setLastMemory(false);

                mPlayerCallback.onChangeLastMemory(0);
            }

            if (init) {
                if (mThread0 != null) {
                    mThread0.stopMusic();
                    mThread0 = null;
                }

                if (mThread1 != null) {
                    mThread1.stopMusic();
                    mThread1 = null;
                }

                mThread0 = new PlayMusicThread(playerType, index);
                mThread0.setDataSource(url);
                mThread0.start();

                mPlayThread = PLAY_THREAD_0;

                Log.e(TAG, "isLastMemory " + cacheInfo.isLastMemory());
                if (cacheInfo.isLastMemory()) {
                    cacheInfo.setLastMemory(false);
                } else {
                    mThread0.playMusic();
                }

                Log.e(TAG, "send actionPrepare " + mDuration);
                mPlayerCallback.onPreparedCache((int) mDuration);
            } else {
                Log.e(TAG, "mPlayThread " + mPlayThread);
                Log.e(TAG, "url " + url);
                if (mPlayThread == PLAY_THREAD_0) {
                    if (mThread1 != null) {
                        mThread1.stopMusic();
                        mThread1 = null;
                    }

                    mThread1 = new PlayMusicThread(playerType, index);
                    mThread1.setDataSource(url);
                } else if (mPlayThread == PLAY_THREAD_1) {
                    if (mThread0 != null) {
                        mThread0.stopMusic();
                        mThread0 = null;
                    }

                    mThread0 = new PlayMusicThread(playerType, index);
                    mThread0.setDataSource(url);
                }

                Log.e(TAG, "mIsNeedNextSection : " + mIsNeedNextSection);
                if (mIsNeedNextSection) {
                    mIsNeedNextSection = false;
                    mIsRequestNextSection = true;

                    mThread0.start();

                    if (cacheInfo.isLastMemory()) {
                        cacheInfo.setLastMemory(false);
                        mPlayerCallback.onPreparedCache((int) mDuration);
                    } else {
                        mThread0.playMusic();
                        mPlayerCallback.onPlayCache(mCurrentPosition);
                    }
                }
            }

            printNetworkUsage(mContext);
        }

        @Override
        public void onInfoChanged(int totalFileSize, int headerSize) {
            Log.e(TAG, "onInfoChanged() " + totalFileSize + " " + headerSize);
            mFileTotalSize = totalFileSize - headerSize;
            mTotalIndex = mFileTotalSize / STREAMING_BUFFER_BLOCK;

            if (mFileTotalSize % STREAMING_BUFFER_BLOCK == 0) {
                mTotalIndex -= 1;
            }
        }

        @Override
        public synchronized void onBufferingFinish(String url, int index, String playerType) {
            Log.e(TAG, "onBufferingFinish " + url);
            mIsComplete = false;

            if (mThread0 != null) {
                mThread0.stopMusic();
                mThread0 = null;
            }

            if (mThread1 != null) {
                mThread1.stopMusic();
                mThread1 = null;
            }

            mPlayThread = PLAY_THREAD_0;

            mThread0 = new PlayMusicThread(playerType, index);
            mThread0.setDataSource(url);

            if (cacheInfo.isLastMemory()) {
                if (checkNeedNextSection()) {
                    int nextIndex = mCurrentIndex + 1;
                    cacheManager.requestNextStreaming(cacheInfo, nextIndex);
                } else {
                    cacheInfo.setLastMemory(false);
                    mPlayerCallback.onPreparedCache((int) mDuration);
                }
            } else {
                mThread0.seekTo(mCurrentPosition);

                if (checkNeedNextSection()) {
                    int nextIndex = mCurrentIndex + 1;
                    cacheManager.requestNextStreaming(cacheInfo, nextIndex);
                } else {
                    mThread0.playMusic();
                    mPlayerCallback.onPlayCache(mCurrentPosition);
                }
            }

            if (!mIsNeedNextSection) {
                mThread0.start();
            }
            printNetworkUsage(mContext);
        }

        @Override
        public int getFileTotalSize() {
            return mFileTotalSize;
        }

        @Override
        public void ChangeLastMemory(boolean changedLastMemory) {
            this.isChangedLastMemory = changedLastMemory;
        }

        @Override
        public void initRequestNextSection() {
            Log.e(TAG, "initRequestNextSection");
            mIsRequestNextSection = false;
            mIsRetryNextSection = true;
        }

        @Override
        public void onError() {
            stop();
            init();
            destroy();

            mPlayerCallback.onTimeoutCache();
        }

        @Override
        public void onCompleteCache() {
            mPlayerCallback.onCompleteCache();
        }

        @Override
        public void setProgressNextDownload(boolean b) {
            mIsProgressNextDownload = b;
        }

        @Override
        public void onEmptyCacheParams() {

        }
    };

    private boolean checkNeedNextSection() {
        Log.e(TAG, "checkNeedNextSection()");
        mIsNeedNextSection = false;

        if (cacheInfo.getPlayMode() == CacheInfo.PLAY_MODE_DIVIDE_FILE) {
            if (mCurrentPosition != 0 && (mCurrentPosition % mFile_section == 0)) {
                mIsNeedNextSection = true;
            } else {
                int startSection = 0;

                if (mCurrentIndex == 0) {
                    startSection = mFile_request_section;
                } else {
                    int index = mCurrentIndex;
                    startSection = (index * mFile_section) + mFile_request_section;
                }

                Log.e(TAG, "startSection : " + startSection);
                Log.e(TAG, "currentPosition : " + mCurrentPosition);

                if (mCurrentPosition >= startSection) {
                    int index = mCurrentIndex + 1;
                    Log.e(TAG, "index : " + index);
                    Log.e(TAG, "totalIndex : " + mTotalIndex);

                    if (index <= mTotalIndex) {
                        mIsNeedNextSection = true;
                    }
                }
            }
        }
        return mIsNeedNextSection;
    }


    /**********
     * Network Data Usage Check
     **********/

    private void printNetworkUsage(Context context) {
        if (mPackageUid == -1) {
            List<ApplicationInfo> packages = null;

            try{
                PackageManager pm = context.getPackageManager();
                packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            }catch (Exception e){
                packages = null;
            }

            if(packages == null){
                return;
            }
            for (ApplicationInfo packageInfo : packages) {
                if (packageInfo.packageName.equals(this.mPackageName)) {
                    mPackageUid = packageInfo.uid;
                    break;
                }
            }
        }

        long rx = TrafficStats.getUidRxBytes(mPackageUid);
        long tx = TrafficStats.getUidTxBytes(mPackageUid);

        Log.e("printNetworkUsage", "###############");
        Log.e("printNetworkUsage", "rx : " + getFormmat(rx));
        Log.e("printNetworkUsage", "tx : " + getFormmat(tx));
        Log.e("printNetworkUsage", "total : " + getFormmat((rx + tx)));
        Log.e("printNetworkUsage", "###############");
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
}
