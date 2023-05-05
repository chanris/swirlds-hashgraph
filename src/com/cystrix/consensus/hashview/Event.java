package com.cystrix.consensus.hashview;

import com.cystrix.consensus.Tx;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 *  在hashgraph上的事件实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Event implements Cloneable {
    //private Event selfParentRef;
    //private Event otherParentRef;

    // 每个成员的第一个事件的自父亲坐标为 “x,-1”
    private String selfParentHash;  // 4,1   十位上是成员id, 个位是成员维护的平行链上的事件序号（id、事件序号都是从0开始）
    private String otherParentHash; // 3,2

    private List<Tx> txList;
    private Long  timestamp;
    private Long consensusTimestamp; // 共识时间戳
    private Boolean isWitness; // 是否是见证人
    private Boolean isFamous;  // 见证人是否著名
    private Boolean isCommit; // 是否达成共识

    private  Integer createdRound = Integer.MIN_VALUE; // 创建轮次
    private  Integer receivedRound = Integer.MIN_VALUE; // 接收轮次

    private String sign;

    // 初始化一个平行链专用
    public Event(long timestamp, String selfParentHash) {
        this.timestamp = timestamp;
        this.selfParentHash = selfParentHash;
        this.setCreatedRound(1);
        this.setIsWitness(true);
    }

    public Event clone() {
        Event clone;
        try {
            clone = (Event) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        List<Tx> txes = new ArrayList<>();
        this.txList.forEach(tx->{
            txes.add(tx.clone());
        });
        clone.setTxList(txes);
        return clone;
    }

    public String toString(){
        String[] crdPStr = this.selfParentHash.split(",");
        int row = Integer.parseInt(crdPStr[0]) + 1;
        int line = Integer.parseInt(crdPStr[1]);
        return  "(" + row + "," + line +": "+ this.isFamous + ": " + this.consensusTimestamp +")";
    }
}
