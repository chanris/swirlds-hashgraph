package com.chenyue.util;


import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.codec.binary.Hex;

import java.nio.charset.StandardCharsets;
import java.security.*;

/**
 * RSA非对称加密算法，产生系统的账户
 */
public class RSAUtils {
    public static void main(String[] args) throws Exception {
      /*  // generate key pair
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();
        // sign message
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        byte[] message2 = "Hello, world!".getBytes();
        signature.update(message2);
        byte[] signatureBytes = signature.sign();
        // verify signature
        signature.initVerify(publicKey);
        signature.update(message2);
        boolean verified  = signature.verify(signatureBytes);
        System.out.println("Signature verified: " + verified);*/

        KeyPair keyPair = generateKeyPair();
        // String json = JSONObject.toJSONString(keyPair.getPublic());

        // System.out.println(Hex.encodeHex(keyPair.getPublic().getEncoded()));

        byte[] digest =  signMsg("{id: 1, name: \"chenyue\"}", keyPair.getPrivate());
        digest[0]= 0b1;
        digest[1]= 0b1;
        System.out.println("验签结果:" + verifySign("{id: 1, name: \"chenyue\"}",digest, keyPair.getPublic() ));


    }

    /**
     * 产生公钥对
     * @return 公钥对
     */
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }

    /**
     * 使用私钥对消息进行签名,返回签名的摘要
     * @param jsonStr
     * @param privateKey
     */
    public static byte[] signMsg(String jsonStr, PrivateKey privateKey) throws Exception {
        // sign message
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(jsonStr.getBytes(StandardCharsets.UTF_8));
        return signature.sign();
    }

    public static byte[] signMsg(String jsonStr, String privateKeyJson) throws  Exception {
        PrivateKey privateKey = JSONObject.parseObject(privateKeyJson, PrivateKey.class);
        return  signMsg(jsonStr, privateKey);
    }

    /**
     * 使用公钥进行验签，返回验签结果
     * @param digest
     * @param publicKey
     * @return
     */
    public static boolean verifySign(String message, byte[] digest,  PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        // verify signature
        signature.initVerify(publicKey);
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        return signature.verify(digest);
    }

    public static boolean verifySign(String message, byte[] digest, String publicKeyJson) throws  Exception {
        PublicKey publicKey = JSONObject.parseObject(publicKeyJson, PublicKey.class);
        return true;
    }


}

