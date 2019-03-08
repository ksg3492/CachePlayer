package com.sunggil.cacheplayer.db;

import android.content.Context;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;
import android.util.Log;

class CacheDBHelper extends SQLiteOpenHelper {
	private static final String TAG = "CacheDBHelper";

	public static final String CACHE_DB_NAME = "db_cache";
	public static final String CACHE_TB_NAME = "tb_cache";
	public static final String CACHE_COLUMN_FILE_INFO = "file_info";
	public static final String CACHE_COLUMN_FILE_NAME = "file_name";
	public static final String CACHE_COLUMN_FILE_URL = "file_url";
	public static final String CACHE_COLUMN_FILE_SIZE = "file_size";
	public static final String CACHE_COLUMN_DATE = "date";
	public static final String CACHE_COLUMN_TYPE = "type";

	public static final String CACHE_DIVIDE_TB_NAME = "tb_cache_divide";
	public static final String CACHE_COLUMN_DIVIDE_FILE_URL = "divide_file_url";
	public static final String CACHE_COLUMN_DIVIDE_TOTAL_FILE_SIZE = "divide_total_file_size";
	public static final String CACHE_COLUMN_DIVIDE_HEADER_SIZE = "divide_header_size";

	public CacheDBHelper(Context context, String name, SQLiteDatabase.CursorFactory factory,
						 int version) {
		super(context, name, factory, version);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		String query = "CREATE TABLE IF NOT EXISTS "
				+ CACHE_TB_NAME + " ("
				+ CACHE_COLUMN_FILE_INFO + " BLOB PRIMARY KEY, "
				+ CACHE_COLUMN_FILE_NAME + " TEXT NOT NULL, "
				+ CACHE_COLUMN_FILE_URL + " TEXT, "
				+ CACHE_COLUMN_FILE_SIZE + " INTEGER NOT NULL, "
				+ CACHE_COLUMN_DATE + " TEXT NOT NULL, "
				+ CACHE_COLUMN_TYPE + " TEXT NOT NULL)";
		Log.e(TAG, "query " + query);
		try{
			db.execSQL(query);
		}catch (Exception e){
			Log.e(TAG, " "+e);
		}

		query = "CREATE TABLE IF NOT EXISTS "
				+ CACHE_DIVIDE_TB_NAME + " ("
				+ CACHE_COLUMN_DIVIDE_FILE_URL + " TEXT PRIMARY KEY, "
				+ CACHE_COLUMN_DIVIDE_TOTAL_FILE_SIZE + " INTEGER NOT NULL, "
				+ CACHE_COLUMN_DIVIDE_HEADER_SIZE + " INTEGER NOT NULL, "
				+ "FOREIGN KEY(" + CACHE_COLUMN_DIVIDE_FILE_URL + ") REFERENCES " + CACHE_TB_NAME + "(" + CACHE_COLUMN_FILE_URL + "))";
		Log.e(TAG, "query " + query);
		try{
			db.execSQL(query);
		}catch (Exception e){
			Log.e(TAG, " "+e);
		}
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	}
}
