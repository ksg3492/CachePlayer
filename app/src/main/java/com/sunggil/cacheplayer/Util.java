package com.sunggil.cacheplayer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Util {
    public static boolean isNetworkStat(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        try {
            NetworkInfo activeNetwork = manager.getActiveNetworkInfo();
            return activeNetwork.isConnected();
        } catch (Exception e) {
            Log.e("Util","Util.isNetworkStat Error : ", e);
        }
        return false;
    }

    public static String getFormmatedNowDate(String format) {
        String date = null;

        try {
            SimpleDateFormat d1 = new SimpleDateFormat(format);
            Date now = new Date();
            date = d1.format(now);
        } catch (Exception e) {
            Log.e("Util", "", e);
        }

        return date;
    }
}