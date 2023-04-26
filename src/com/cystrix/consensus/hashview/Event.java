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
    private long  timestamp;
    private long consensusTimestamp; // 共识时间戳
    private boolean isWitness; // 是否是见证人
    private boolean isFamous;  // 见证人是否著名
    private boolean isCommit; // 是否达成共识

    private  int createdRound = Integer.MIN_VALUE; // 创建轮次
    private  int receivedRound = Integer.MIN_VALUE; // 接收轮次

    // 初始化一个平行链专用
    public Event(long timestamp, String selfParentHash) {
        this.timestamp = timestamp;
        this.selfParentHash = selfParentHash;
        this.setCreatedRound(1);
        this.setWitness(true);
    }

    public Event clone() {
        Event clone = null;
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

    public static void main(String[] args) throws CloneNotSupportedException {
        //
        Tx tx = new Tx();
        Tx tx2 = tx.clone();
        List<Tx> txes = new ArrayList<>();

        txes.add(tx);
        txes.add(tx2);

        Event event = new Event();
        event.setTxList(txes);
        Event e = event.clone();
        System.out.println(e.getTxList() == txes);
        System.out.println(e.getTxList().get(0) == txes.get(0));
    }
}
