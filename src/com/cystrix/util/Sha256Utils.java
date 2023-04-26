package com.cystrix.util;


import com.alibaba.fastjson2.JSONObject;
import com.cystrix.consensus.Tx;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Sha256Utils {

    /**
     * 利用java原生的类实现SHA256加密
     * @param origin 加密后的报文
     * @return
     */
    public static String getSHA256(String origin){
        MessageDigest messageDigest;
        String encodestr = "";
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(origin.getBytes(StandardCharsets.UTF_8));
            encodestr = byte2Hex(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return encodestr;
    }

    /**
     * 将byte转为16进制
     * @param bytes
     * @return
     */
    private static String byte2Hex(byte[] bytes){
        StringBuffer stringBuffer = new StringBuffer();
        String temp = null;
        for (int i=0;i<bytes.length;i++){
            temp = Integer.toHexString(bytes[i] & 0xFF);
            if (temp.length()==1){
                //1得到一位的进行补0操作
                stringBuffer.append("0");
            }
            stringBuffer.append(temp);
        }
        return stringBuffer.toString();
    }


    public static void main(String[] args) {
        Tx tx = new Tx();
        tx.setBalance(500);
        // Object ==> JSON String
        String  txStr = JSONObject.toJSONString(tx);
        // JSON String ==> Object
        Tx tx1 = JSONObject.parseObject(txStr, Tx.class);
        System.out.println(tx1);

        String hash = Sha256Utils.getSHA256(JSONObject.toJSONString(tx));
        System.out.println("0x" + hash);
    }


}
