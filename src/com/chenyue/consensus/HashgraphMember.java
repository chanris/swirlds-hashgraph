package com.chenyue.consensus;

import com.chenyue.consensus.hashview.Event;

import com.chenyue.util.Graph;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 成员节点
 * 功能：与其他成员通过gossip协议通信广播事件、接收事件、验证事件，并打包客户端发来的交易，维护本地的视图（由一次次的gossip通信构成）
 *
 */
@Data
@Slf4j
public class HashgraphMember {
    private Integer id;
    private String name;
    private ConcurrentHashMap<Integer, List<Event>> hashgraph; // 保留完整的hashgraph 事件信息
    private Graph graph; // 只保留事件之间的引用关系
    private int numMembers;
    private Map<Integer, List<Event>> witnessMap; // 存放每轮的witness

    public HashgraphMember(Integer id, String name, int numMembers) {
        this.id = id;
        this.name = name;
        this.hashgraph = new ConcurrentHashMap<>();
        this.witnessMap = new HashMap<>();
        this.numMembers = numMembers;
        for (int i = 0; i < this.numMembers; i++) {
            List<Event> chain = new ArrayList<>();
            ArrayList<Event> events = new ArrayList<>();
            // 初始化一个事件在自己的平行链上，作为最初的见证人
            if (i == id) {
                chain.add(new Event(System.currentTimeMillis(), id+",-1"));
            }
            this.hashgraph.put(i,chain);
            this.witnessMap.put(i+1, events);
        }
        this.graph = new Graph(this.hashgraph, this.numMembers);
    }

    public boolean addEvent(Event event) {
        try {
            int selfPX, selfPY, otherPX, otherPY;
            String selfParentCoordinate = event.getSelfParentHash();
            String otherParentCoordinate = event.getOtherParentHash();
            selfPX = Integer.parseInt(selfParentCoordinate.split(",")[0]);
            selfPY = Integer.parseInt(selfParentCoordinate.split(",")[1]);
            int size =  this.hashgraph.get(selfPX).size();

            // 添加平行链上的第一个事件
            if (size == 0 && -1 == selfPY) {
                this.getHashgraph().get(selfPX).add(event);
                return true;
            }
            otherPX = Integer.parseInt(otherParentCoordinate.split(",")[0]);
            otherPY = Integer.parseInt(otherParentCoordinate.split(",")[1]);

            Event selfParentEvent = this.hashgraph.get(selfPX).get(selfPY);
            Event otherParentEvent = this.hashgraph.get(otherPX).get(otherPY);
            if (selfParentEvent != null && otherParentEvent != null && (selfPY + 1 == size)) {
                this.hashgraph.get(selfPX).add(event);
                // 在graph上构造有向边
                this.graph.addEdge(selfPX + numMembers * (selfPY + 1), selfPX + numMembers * selfPY);
                this.graph.addEdge(selfPX + numMembers * (selfPY + 1), otherPX + numMembers * otherPY);
                return true;
            }
            return false;
        }catch (Exception e) {
           // log.warn("node_id:{} 添加边 异常:{}", this.id, e.getMessage());
            return false;
        }
    }

    public void divideRounds() {
        this.hashgraph.forEach((id,chain)->{
            for (int i = 0; i < chain.size(); i++) {
                int r;
                if (i == 0) {
                    Event e = chain.get(0);
                    e.setCreatedRound(1);
                    e.setWitness(true);
                    this.witnessMap.get(1).add(e);
                }else {
                    Event preEvent = chain.get(i-1);
                    r = preEvent.getCreatedRound();
                    Event e = chain.get(i);

                    witnessMap.forEach((round, witnessList)->{
                        AtomicInteger vote = new AtomicInteger();
                        if (r == round) {
                            witnessList.forEach(witness->{
                                if (isStronglySee(e, witness)) {
                                    vote.getAndIncrement();
                                }
                            });
                            if (vote.get() > (2 * numMembers / 3)) {
                                e.setWitness(true);
                                e.setCreatedRound(r+1);
                            }else {
                                e.setWitness(false);
                                e.setCreatedRound(r);
                            }
                        }
                    });
                }
            }
        });
    }

    /**
     * 判断事件src是否强可见事件dest
     * @param src
     * @param dest
     * @return 是否强可见
     */
    private boolean isStronglySee(Event src, Event dest) {
        AtomicBoolean res = new AtomicBoolean(false);
        String[] crd = src.getSelfParentHash().split(",");
        String[] crd2 = src.getOtherParentHash().split(",");
        int destIdx = Integer.parseInt(crd[0]) + numMembers * Integer.parseInt(crd[1]);
        int srcIdx = Integer.parseInt(crd2[0]) + numMembers * Integer.parseInt(crd2[1]);
        List<List<Integer>> allPaths = graph.findAllPaths(srcIdx, destIdx);
        try {
            allPaths.forEach(path->{
                Set<Integer> memberIds = new HashSet<>();
                path.forEach(index->{
                    memberIds.add(index%numMembers);
                    if (memberIds.size() > (numMembers*2/3)) {
                        res.set(true);
                        throw new RuntimeException();
                    }
                });
            });
        }catch (Exception e) {}
        return res.get();
    }

    public void traversal() {
//        divideRounds();
        this.getHashgraph().forEach((id, chain)->{
//            chain.getEventList().forEach(item-> System.out.printf("[%s,%d] r: %d",item.getSelfParentHash().split(",")[0],
//                    (Integer.parseInt(item.getSelfParentHash().split(",")[1]) + 1), item.getCreatedRound()));
            chain.forEach(item-> System.out.printf("[%s,%d]",item.getSelfParentHash().split(",")[0],
                    (Integer.parseInt(item.getSelfParentHash().split(",")[1]) + 1)));
            System.out.println();
        });
    }

    public static void main(String[] args) {
        HashgraphMember member = new HashgraphMember(0, "Member-1", 5);

        Event e = new Event();
        Event e1 = new Event();
        Event e2 = new Event();
        Event e3 = new Event();
        Event e4 = new Event();
        Event e5 = new Event();
        Event e6 = new Event();
        Event e7 = new Event();
        Event e8 = new Event();
        Event e9 = new Event();
        Event e10 = new Event();
        Event e11 = new Event();
        Event e12 = new Event();
        Event e14 = new Event();
        Event e17 = new Event();
        Event e22 = new Event();
        Event e27 = new Event();

        e.setSelfParentHash("0,-1");
        e1.setSelfParentHash("1,-1");
        e2.setSelfParentHash("2,-1");
        e3.setSelfParentHash("3,-1");
        e4.setSelfParentHash("4,-1");

        e5.setSelfParentHash("0,-1");
        e6.setSelfParentHash("1,-1");
        e7.setSelfParentHash("2,-1");
        e8.setSelfParentHash("3,-1");
        e9.setSelfParentHash("4,-1");

//        member.addEvent(e);
        member.addEvent(e1);
        member.addEvent(e2);
        member.addEvent(e3);
        member.addEvent(e4);

         member.traversal();
    }

}
