package com.sunggil.cacheplayer.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;
import android.util.Log;

import com.sunggil.cacheplayer.Util;
import com.sunggil.cacheplayer.db.model.CacheDBFileSizeInfo;
import com.sunggil.cacheplayer.db.model.CacheDBInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CacheDBManager {
	private final String TAG = "CacheDBManager";

	private Context mContext;
	private CacheDBHelper dbManager;
	private SQLiteDatabase db = null;
	private Cursor cursor = null;

	private final int CACHE_DB_VERSION = 1;
	
	private String mAuthKey = null;

	public CacheDBManager(Context context, String authKey) {
		mContext = context;
		this.mAuthKey = authKey;
		dbManager = new CacheDBHelper(context, CacheDBHelper.CACHE_DB_NAME, null, CACHE_DB_VERSION);
	}

	public void resetDBFile(){
		File file = mContext.getDatabasePath(CacheDBHelper.CACHE_DB_NAME);
		if(file != null){
			if(file.exists()){
				file.delete();
				Log.e(TAG,"resetDBFile");
			}
		}
		dbManager = new CacheDBHelper(mContext, CacheDBHelper.CACHE_DB_NAME, null, CACHE_DB_VERSION);
	}


	public List<CacheDBInfo> select(String fileInfo, String fileName) {
		String query = "SELECT "
				+ CacheDBHelper.CACHE_COLUMN_FILE_INFO + ", "
				+ CacheDBHelper.CACHE_COLUMN_FILE_NAME + ", "
				+ CacheDBHelper.CACHE_COLUMN_FILE_SIZE + ", "
				+ CacheDBHelper.CACHE_COLUMN_DATE + ", "
				+ CacheDBHelper.CACHE_COLUMN_TYPE
				+ " FROM " + CacheDBHelper.CACHE_TB_NAME;

		if (fileInfo != null) {
			query += " WHERE " + CacheDBHelper.CACHE_COLUMN_FILE_INFO + "='" + fileInfo + "'";
		} else if (fileName != null) {
			query += " WHERE " + CacheDBHelper.CACHE_COLUMN_FILE_NAME + "='" + fileName + "'";
		}

		query += " ORDER BY " + CacheDBHelper.CACHE_COLUMN_DATE + " ASC";

		return select(query);
	}

	public int selectCount(String fileInfo, String fileName) {
		String query = "SELECT "
				+ CacheDBHelper.CACHE_COLUMN_FILE_INFO + ", "
				+ CacheDBHelper.CACHE_COLUMN_FILE_NAME + ", "
				+ CacheDBHelper.CACHE_COLUMN_FILE_SIZE + ", "
				+ CacheDBHelper.CACHE_COLUMN_DATE + ", "
				+ CacheDBHelper.CACHE_COLUMN_TYPE
				+ " FROM " + CacheDBHelper.CACHE_TB_NAME;

		if (fileInfo != null) {
			query += " WHERE " + CacheDBHelper.CACHE_COLUMN_FILE_INFO + "='" + fileInfo + "'";
		} else if (fileName != null) {
			query += " WHERE " + CacheDBHelper.CACHE_COLUMN_FILE_NAME + "='" + fileName + "'";
		}

		return selectCount(query);
	}

	public List<CacheDBFileSizeInfo> selectTotalFileSize(String url) {
		String query = "SELECT "
				+ CacheDBHelper.CACHE_COLUMN_DIVIDE_FILE_URL + ", "
				+ CacheDBHelper.CACHE_COLUMN_DIVIDE_TOTAL_FILE_SIZE + ", "
				+ CacheDBHelper.CACHE_COLUMN_DIVIDE_HEADER_SIZE + " "
				+ "FROM " + CacheDBHelper.CACHE_DIVIDE_TB_NAME + " ";

		if (url != null) {
			query += "WHERE " + CacheDBHelper.CACHE_COLUMN_DIVIDE_FILE_URL + " = '" + url + "'";
		}

		return selectTotalFileSizeInfo(query);
	}

	public long insert(String fileInfo, String fileName, int fileSize, String cacheType) {
		Log.e(TAG, "insert()");
		ContentValues values = new ContentValues();
		values.put(CacheDBHelper.CACHE_COLUMN_FILE_INFO, fileInfo);
		values.put(CacheDBHelper.CACHE_COLUMN_FILE_NAME, fileName);
		values.put(CacheDBHelper.CACHE_COLUMN_FILE_SIZE, fileSize);
		values.put(CacheDBHelper.CACHE_COLUMN_DATE, Util.getFormmatedNowDate("yyyyMMddHHmmss"));
		values.put(CacheDBHelper.CACHE_COLUMN_TYPE, cacheType);

		Log.e(TAG, CacheDBHelper.CACHE_COLUMN_FILE_INFO + " : " + fileInfo);
		Log.e(TAG, CacheDBHelper.CACHE_COLUMN_FILE_NAME + " : " + fileName);
		Log.e(TAG, CacheDBHelper.CACHE_COLUMN_FILE_SIZE + " : " + fileSize);
		Log.e(TAG, CacheDBHelper.CACHE_COLUMN_DATE + " : " + Util.getFormmatedNowDate("yyyyMMddHHmmss"));
		Log.e(TAG, CacheDBHelper.CACHE_COLUMN_TYPE + " : " + cacheType);

		return insert(values);
	}

	public long insertTotalFileSize(String url, int totalFileSize, int headerSize) {
		ContentValues values = new ContentValues();
		values.put(CacheDBHelper.CACHE_COLUMN_DIVIDE_FILE_URL, url);
		values.put(CacheDBHelper.CACHE_COLUMN_DIVIDE_TOTAL_FILE_SIZE, totalFileSize);
		values.put(CacheDBHelper.CACHE_COLUMN_DIVIDE_HEADER_SIZE, headerSize);

		Log.e(TAG, CacheDBHelper.CACHE_COLUMN_DIVIDE_FILE_URL + " : " + url);
		Log.e(TAG, CacheDBHelper.CACHE_COLUMN_DIVIDE_TOTAL_FILE_SIZE + " : " + totalFileSize);
		Log.e(TAG, CacheDBHelper.CACHE_COLUMN_DIVIDE_HEADER_SIZE + " : " + headerSize);

		return insertFileSize(values);
	}

	public void updateTotalFileSize(String url, int totalFileSize, int headerSize) {
		String query = "UPDATE " + CacheDBHelper.CACHE_DIVIDE_TB_NAME
				+ " SET " + CacheDBHelper.CACHE_COLUMN_DIVIDE_TOTAL_FILE_SIZE + " = " + totalFileSize
				+ ", " + CacheDBHelper.CACHE_COLUMN_DIVIDE_HEADER_SIZE + " = " + headerSize
				+ " WHERE " + CacheDBHelper.CACHE_COLUMN_DIVIDE_FILE_URL + " = '" + url + "'";

		Log.e(TAG, query);

		update(query);
	}

	public void delete(List<String> args) {
		getWritableDB();

		if(db == null){
			return;
		}

		try {
			for (String arg : args) {
				int count = 0;

				try {
					count = db.delete(CacheDBHelper.CACHE_TB_NAME, CacheDBHelper.CACHE_COLUMN_FILE_NAME + "=?", new String[]{arg});
					Log.e(TAG, CacheDBHelper.CACHE_TB_NAME + " affected (" + count + ") rows");
				} catch (Exception e) {
					Log.e(TAG, "", e);
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "", e);
		} finally {
			Log.e(TAG, "delete()");
			closeCursor();
		}
	}

	public void delete(String arg) {
		List<String> args = new ArrayList<String>();
		args.add(arg);
		delete(args);
	}

	public void deleteAll() {

		getWritableDB();
		if(db == null){
			return;
		}

		try {
			String query = "DELETE FROM " + CacheDBHelper.CACHE_TB_NAME;
			Log.e(TAG, query);
			db.execSQL(query);

			query = "DELETE FROM " + CacheDBHelper.CACHE_DIVIDE_TB_NAME;
			Log.e(TAG, query);
			db.execSQL(query);
		} catch (Exception e) {
			Log.e(TAG, "", e);
		} finally {
			Log.e(TAG, "deleteAll()");
			closeCursor();
		}
	}

	public void updateDate(CacheDBInfo domain) {
		String query = "UPDATE " + CacheDBHelper.CACHE_TB_NAME
				+ " SET " + CacheDBHelper.CACHE_COLUMN_DATE + " = '" + Util.getFormmatedNowDate("yyyyMMddHHmmss") + "'"
				+ " WHERE " + CacheDBHelper.CACHE_COLUMN_FILE_NAME + " = '" + domain.getFileName() + "'"
				+ " AND " + CacheDBHelper.CACHE_COLUMN_FILE_INFO + " = '" + domain.getFileInfo() + "'";

		Log.e(TAG, query);

		update(query);
	}

	public void updateSize(String fileName, int fileSize) {
		Log.e(TAG, "updateSize " + fileName + " " + fileSize);
		String query = "UPDATE " + CacheDBHelper.CACHE_TB_NAME
				+ " SET " + CacheDBHelper.CACHE_COLUMN_FILE_SIZE + " = " + fileSize
				+ " WHERE " + CacheDBHelper.CACHE_COLUMN_FILE_NAME + " = '" + fileName + "'";

		Log.e(TAG, query);


		update(query);
	}

	private synchronized void getWritableDB(){
		if (db != null) {
			return;
		}
		try{
			db = dbManager.getWritableDatabase(mAuthKey);
		}catch (Exception e){
			Log.e(TAG," "+e);
			if(mAuthKey != null){
				try{
					resetDBFile();
					db = dbManager.getWritableDatabase(mAuthKey);
				}catch (Exception e1){
					db = null;
					return;
				}
			}else{
				db = null;
				return;
			}
		}
	}

	private List<CacheDBInfo> select(String query) {
		Log.e(TAG, query);

		List<CacheDBInfo> list = new ArrayList<CacheDBInfo>();

		getWritableDB();
		if(db == null){
			return list;
		}

		try {
			cursor = db.rawQuery(query, null);

			while (cursor.moveToNext()) {
				try {
					String cacheFileInfo = cursor.getString(cursor.getColumnIndex(CacheDBHelper.CACHE_COLUMN_FILE_INFO));
					String cacheFileName = cursor.getString(cursor.getColumnIndex(CacheDBHelper.CACHE_COLUMN_FILE_NAME));
					int cacheFileSize = cursor.getInt(cursor.getColumnIndex(CacheDBHelper.CACHE_COLUMN_FILE_SIZE));
					String cacheDate = cursor.getString(cursor.getColumnIndex(CacheDBHelper.CACHE_COLUMN_DATE));
					String cacheType = cursor.getString(cursor.getColumnIndex(CacheDBHelper.CACHE_COLUMN_TYPE));

					CacheDBInfo domain = new CacheDBInfo();

					domain.setFileInfo(cacheFileInfo);
					domain.setFileName(cacheFileName);
					domain.setFileSize(cacheFileSize);
					domain.setDate(cacheDate);
					domain.setType(cacheType);

					list.add(domain);
				} catch (Exception e) {
					Log.e(TAG, "", e);
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "", e);
		} finally {
			Log.e(TAG, "select");
			closeCursor();
		}

		return list;
	}

	private List<CacheDBFileSizeInfo> selectTotalFileSizeInfo(String query) {
		Log.e(TAG, query);

		List<CacheDBFileSizeInfo> infos = new ArrayList<CacheDBFileSizeInfo>();

		getWritableDB();
		if(db == null){
			return infos;
		}

		try {
			cursor = db.rawQuery(query, null);

			while (cursor.moveToNext()) {
				try {
					CacheDBFileSizeInfo info = new CacheDBFileSizeInfo();

					String cacheUrl = cursor.getString(cursor.getColumnIndex(CacheDBHelper.CACHE_COLUMN_DIVIDE_FILE_URL));
					int cacheTotalFileSize = cursor.getInt(cursor.getColumnIndex(CacheDBHelper.CACHE_COLUMN_DIVIDE_TOTAL_FILE_SIZE));
					int headerSize = cursor.getInt(cursor.getColumnIndex(CacheDBHelper.CACHE_COLUMN_DIVIDE_HEADER_SIZE));

					info.setUrl(cacheUrl);
					info.setTotalFileSize(cacheTotalFileSize);
					info.setHeaderSize(headerSize);

					infos.add(info);
				} catch (Exception e) {
					Log.e(TAG, "", e);
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "", e);
		} finally {
			Log.e(TAG, "selectTotalFileSizeInfo");
			closeCursor();
		}

		return infos;
	}

	private int selectCount(String query) {
		Log.e(TAG, query);
		int count = 0;

		getWritableDB();
		if(db == null){
			return count;
		}

		try {
			cursor = db.rawQuery(query, null);

			while (cursor.moveToNext()) {
				count++;
			}
		} catch (Exception e) {
			Log.e(TAG, "", e);
		} finally {
			Log.e(TAG, "selectCount");
			closeCursor();
		}

		return count;
	}

	private long insert(ContentValues values) {
		long result = -1;

		getWritableDB();
		if(db == null){
			return result;
		}

		try {
			result = db.insert(CacheDBHelper.CACHE_TB_NAME, null, values);
		} catch (Exception e) {
			Log.e(TAG, "", e);
		} finally {
			Log.e(TAG, "insert");
			closeCursor();
		}

		return result;
	}

	private long insertFileSize(ContentValues values) {
		long result = 0;

		getWritableDB();
		if(db == null){
			return result;
		}

		try {
			result = db.insert(CacheDBHelper.CACHE_DIVIDE_TB_NAME, null, values);
		} catch (Exception e) {
			Log.e(TAG, "", e);
		} finally {
			Log.e(TAG, "insertFileSize");
			closeCursor();
		}

		return result;
	}

	private void update(String query) {
		getWritableDB();
		if(db == null){
			return ;
		}

		try {
			db.execSQL(query);
		} catch (Exception e) {
			Log.e(TAG, "", e);
		} finally {
			Log.e(TAG, "update");
			closeCursor();
		}
	}

	private void closeCursor() {
		Log.e(TAG, "closeCursor");
		if (cursor != null) {
			try {
				cursor.close();
				cursor = null;
			} catch (Exception e) {
				Log.e(TAG, "", e);
			}
		}
	}

	public void closeDB() {
        if (db != null) {
            try {
                db.close();
                db = null;
            } catch (Exception e) {
                Log.e(TAG, "", e);
            }
        }
    }

	public void printDB() {
		Log.e(TAG, "printDB");
		List<CacheDBInfo> list = new ArrayList<CacheDBInfo>();
		List<CacheDBFileSizeInfo> sizeList = new ArrayList<CacheDBFileSizeInfo>();

		list = select(null, null);
		sizeList = selectTotalFileSize(null);

		int count = list.size();

		Log.e(TAG, "Table [" + CacheDBHelper.CACHE_TB_NAME + "] has (" + count + ")rows\n\n");

		if (count > 0) {
			for (int i=0; i<count; i++) {
				String str = "";
				CacheDBInfo domain = list.get(i);

				str += "row " + i + "\n";
				str += "[FileInfo] " + domain.getFileInfo() + "\n";
				str += "[FileName] " + domain.getFileName() + "\n";
				str += "[Date] " + domain.getDate() + "\n";
				str += "[Size] " + domain.getFileSize() + "\n";
				str += "[Type] " + domain.getType() + "\n";

				Log.e(TAG, str);
			}
		}

		count = sizeList.size();

		Log.e(TAG, "Table [" + CacheDBHelper.CACHE_DIVIDE_TB_NAME + "] has (" + count + ")rows");

		if (sizeList.size() > 0) {
			for (int i=0; i<count; i++) {
				String str = "";
				CacheDBFileSizeInfo info = sizeList.get(i);

				str += "row " + i + "\n";
				str += "[Url] " + info.getUrl() + "\n";
				str += "[Size] " + info.getTotalFileSize() + "\n";

				Log.e(TAG, str);
			}
		}
	}
}
