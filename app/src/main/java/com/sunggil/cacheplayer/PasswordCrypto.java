package com.sunggil.cacheplayer;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class PasswordCrypto {
	public static final String TAG = "PasswordCrypto";
	
    public static String encrypt(String seed, String cleartext) throws Exception {
        byte[] rawKey = generateKey(seed.getBytes());
        byte[] result = encrypt(rawKey, cleartext.getBytes());
        return toHex(result);
    }
    
    public static String decrypt(String seed, String encrypted) throws Exception {
        byte[] rawKey = generateKey(seed.getBytes());
        byte[] enc = toByte(encrypted);
        byte[] result = decrypt(rawKey, enc);
        return new String(result);
    }

    public static boolean encrypt(String seed, File file) throws Exception {
        byte[] rawKey = generateKey(seed.getBytes());
        byte[] result = encrypt(rawKey, toByte(file));
        toFile(result, file.getAbsolutePath());
        return true;
    }
    
    public static byte[] decrypt(String seed, File file) throws Exception {
        byte[] rawKey = generateKey(seed.getBytes());
        byte[] result = decrypt(rawKey, toByte(file));
        return result;
    }
    
    public static boolean decryptToFile(String seed, String fromPath, String toPath, String fileName) throws Exception {
        byte[] rawKey = generateKey(seed.getBytes());
        byte[] decByte = decrypt(rawKey, pathToByte(fromPath + fileName));
        File file = toFile(decByte, toPath + fileName);
        
        if (file == null) {
        	return false;
        } else {
        	return true;
        }
    }

//	private static byte[] getRawKey(byte[] seed) throws Exception {
//        KeyGenerator kgen = KeyGenerator.getInstance("AES");
////        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG", "Crypto");
//        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
//        sr.setSeed(seed);
//        kgen.init(128, sr); // 192 and 256 bits may not be available
//        SecretKey skey = kgen.generateKey();
//        byte[] raw = skey.getEncoded();
//        return raw;
//    }

    public static byte[] generateKey(byte[] seed) throws NoSuchAlgorithmException {
        // Generate a 128-bit key
        final int outputKeyLength = 128;

        SecureRandom secureRandom = new SecureRandom();
        secureRandom.setSeed(seed);
        // Do *not* seed secureRandom! Automatically seeded from system entropy.
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(outputKeyLength, secureRandom);
        SecretKey key = keyGenerator.generateKey();
        byte[] raw = key.getEncoded();
        return raw;
    }

    
    private static byte[] encrypt(byte[] raw, byte[] clear) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
        byte[] encrypted = cipher.doFinal(clear);
        return encrypted;
    }

    private static byte[] decrypt(byte[] raw, byte[] encrypted) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, skeySpec);
        byte[] decrypted = cipher.doFinal(encrypted);
        return decrypted;
    }

    public static String toHex(String txt) {
        return toHex(txt.getBytes());
    }
    public static String fromHex(String hex) {
        return new String(toByte(hex));
    }
    
    public static byte[] toByte(String hexString) {
        int len = hexString.length()/2;
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++)
                result[i] = Integer.valueOf(hexString.substring(2*i, 2*i+2), 16).byteValue();
        return result;
    }
    
    public static byte[] pathToByte(String path) {
    	File file = new File(path);
    	
    	if (file.exists()) {
    		return toByte(file);
    	} else {
    		return null;
    	}
    }
    
    public static byte[] toByte(File file) {
    	if (file == null) {
    		return null;
    	}
    	
    	int size = (int) file.length();
    	byte[] bytes = new byte[size];
    	
    	try {
    		BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file));
    		buf.read(bytes, 0, bytes.length);
    		buf.close();
    	} catch (Exception e) {
    		Log.e(TAG, "", e);
    		return null;
    	}
    	
    	return bytes;
    }
    
    public static File toFile(byte[] bytes, String path) {
    	Log.e(TAG, "path " + path);
    	if (bytes == null || bytes.length == 0) {
    		return null;
    	}
    	
    	File file = new File(path);
    	
    	if (file.exists()) {
    		if (file.delete()) {
    			try {
        			file.createNewFile();
    			} catch (Exception e) {
    				Log.e(TAG, "", e);
    			}
    		}
    	}
    	
    	try {
			FileOutputStream fos = new FileOutputStream(file);
			fos.write(bytes);
			fos.close();
		} catch (Exception e) {
			Log.e(TAG, "", e);
		}
    	
    	return file;
    }

    public static String toHex(byte[] buf) {
        if (buf == null)
                return "";
        StringBuffer result = new StringBuffer(2*buf.length);
        for (int i = 0; i < buf.length; i++) {
                appendHex(result, buf[i]);
        }
        return result.toString();
    }
    private final static String HEX = "0123456789ABCDEF";
    private static void appendHex(StringBuffer sb, byte b) {
        sb.append(HEX.charAt((b>>4)&0x0f)).append(HEX.charAt(b&0x0f));
    }
}