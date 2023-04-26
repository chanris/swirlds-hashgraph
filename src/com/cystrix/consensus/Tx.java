package com.cystrix.consensus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Tx implements Serializable, Cloneable {
    private String sender;   // 公钥地址
    private String sign;  // 私钥签名
    private String receiver; // 接收者公钥地址
    private Integer balance;  // 交易金额
    private long timestamp;  // 交易时间戳
    private String extra;  // 交易额外信息


    public Tx clone()  {
        Tx tx;
        try {
            tx = (Tx)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        return tx;
    }
}
