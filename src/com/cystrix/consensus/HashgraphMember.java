package com.cystrix.consensus;

import com.cystrix.consensus.hashview.Event;

import com.cystrix.consensus.hashview.Graph;
import com.cystrix.util.UUIDUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
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
    private ConcurrentHashMap<Integer, List<Event>> witnessMap; // 存放每轮的witness
    private ConcurrentHashMap<String, BigDecimal> ledger; // 分布式账本
    private int c = 10;  // 硬币轮
    private int maxRound = 0;

    public HashgraphMember(Integer id, String name, int numMembers) {
        this.id = id;
        this.name = name;
        this.hashgraph = new ConcurrentHashMap<>();
        this.witnessMap = new ConcurrentHashMap<>();
        this.numMembers = numMembers;
        for (int i = 0; i < this.numMembers; i++) {
            List<Event> chain = new ArrayList<>();

            // 初始化一个事件在自己的平行链上，作为最初的见证人
            if (i == id) {
                Event e = new Event(System.currentTimeMillis(), "-1," + id);
                e.setSign(UUIDUtils.geneUUIDWithoutDash());
                chain.add(e);
            }
            this.hashgraph.put(i,chain);

        }
        this.graph = new Graph(this.hashgraph, this.numMembers);
    }

    public boolean addEvent(Event event) {
        try {
            // int selfPX, selfPY, otherPX, otherPY;
            String selfParentCoordinate = event.getSelfParentHash();
            String otherParentCoordinate = event.getOtherParentHash();
//            selfPX = Integer.parseInt(selfParentCoordinate.split(",")[0]);
//            selfPY = Integer.parseInt(selfParentCoordinate.split(",")[1]);
            int[] selfPCrd =  this.graph.getCrdByCoordinateStr(selfParentCoordinate);

            int size =  this.hashgraph.get(selfPCrd[1]).size();

            // 添加平行链上的第一个事件
            if (size == 0 && -1 == selfPCrd[0]) {
                this.getHashgraph().get(selfPCrd[1]).add(event);
                return true;
            }
            // otherPX = Integer.parseInt(otherParentCoordinate.split(",")[0]);
            // otherPY = Integer.parseInt(otherParentCoordinate.split(",")[1]);
            int[] otherPCrd = this.graph.getCrdByCoordinateStr(otherParentCoordinate);

            Event selfParentEvent = this.hashgraph.get(selfPCrd[1]).get(selfPCrd[0]);
            Event otherParentEvent = this.hashgraph.get(otherPCrd[1]).get(otherPCrd[0]);
            if (selfParentEvent != null && otherParentEvent != null && (selfPCrd[0] + 1 == size)) {
                this.hashgraph.get(selfPCrd[1]).add(event);
                // 在graph上构造有向边
                int idx = this.graph.getIdxByEvent(event);
                int selfIdx = this.graph.getIdxByCoordinateStr(event.getSelfParentHash());
                int otherIdx = this.graph.getIdxByCoordinateStr(event.getOtherParentHash());
                this.graph.addEdge(idx, selfIdx);
                this.graph.addEdge(idx, otherIdx);
                return true;
            }
            return false;
        }catch (Exception e) {
           // log.warn("node_id:{} 添加边 异常:{}", this.id, e.getMessage());
            return false;
        }
    }


    public void divideRounds() {
        //划分轮次之前清口见证人Map
        this.witnessMap.clear();
        AtomicInteger maxLen = new AtomicInteger(Integer.MIN_VALUE);
        List<Integer> chainSizeList = new ArrayList<>(this.numMembers);
        // 获得当前最长链长度，并记录每条链的长度
        this.getHashgraph().forEach((id, chain)->{
            maxLen.set(Math.max(chain.size(), maxLen.get()));
             chainSizeList.add(chain.size());
        });

        // 层次遍历hashgraph，确定每个事件的轮次
        for (int i = 0; i < maxLen.get(); i++) {
            for (int j = 0; j < this.getNumMembers(); j++) {
                // 若该坐标存在事件，则判断其轮次
                if (chainSizeList.get(j) > i) {
                    Event e =  this.hashgraph.get(j).get(i);
                    int r;
                    // 每条链的第一个事件为见证人，并且创建轮次为1
                    if (i == 0) {
                        e.setIsWitness(true);
                        e.setCreatedRound(1);
                        if (!this.witnessMap.containsKey(1)) {
                            this.witnessMap.put(1, new ArrayList<>());
                        }
                        this.witnessMap.get(1).add(e);
                    }else {
                        // 获得父亲事件，父亲事件的轮次r
                        // 如果该事件强可见超过2/3以上r轮的见证人，则该事件为r+1轮
                        Event preEvent = this.hashgraph.get(j).get(i-1);
                        r = preEvent.getCreatedRound();
                        AtomicInteger vote = new AtomicInteger(0);
                        // 获得事件强可见的计票
                        this.witnessMap.get(r).forEach(witness->{
                            if (isStronglySee(e, witness)) {
                                vote.getAndIncrement();
                            }
                        });

                        if (vote.get() > (2 * numMembers / 3)) {
                            e.setIsWitness(true);
                            e.setCreatedRound(r+1);
                            maxRound = Math.max(maxRound, r + 1);
                            if (!witnessMap.containsKey(r+1)) {
                                witnessMap.put(r+1, new ArrayList<>());
                            }
                            witnessMap.get(r+1).add(e);
                        }else {
                            e.setIsWitness(false);
                            e.setCreatedRound(r);
                        }
                    }
                }
            }
        }
        log.debug("划分轮次结束:当前的见证人列表:{}", witnessMap);
    }



    public  void decideFame() {
        this.witnessMap.forEach((r, witnessList)->{
            if (this.witnessMap.containsKey(r+2)) {
                AtomicInteger voteYes = new AtomicInteger();
                AtomicInteger voteNo = new AtomicInteger();
                List<Event> voteEventList = this.witnessMap.get(r+1);
                List<Event> countVoteEventList = this.witnessMap.get(r+2);

//                log.debug("for-pre voteEventList {}", voteEventList);
//                log.debug("for-pre countVoteEventList {}", countVoteEventList);
                for (Event event : witnessList) {
                    for (int i = 0; i < countVoteEventList.size(); i++) {
                        for (int k = 0; k < voteEventList.size(); k++) {
                            if (isSee(voteEventList.get(k), event) && isStronglySee(countVoteEventList.get(i), voteEventList.get(k))) {
                                voteYes.incrementAndGet();
                            }
                            try {
                                if (!isSee(voteEventList.get(k), event) && isStronglySee(countVoteEventList.get(i), voteEventList.get(k))) {
                                    voteNo.incrementAndGet();
                                }
                            }catch (Exception e) {
                                e.printStackTrace();
//                                log.debug("for-in voteEventList {}", voteEventList);
//                                log.debug("for-in countVoteEventList {}", countVoteEventList);
                                throw new RuntimeException(e.getMessage());
                            }

                        }
                        // 超过2/3的见证人都强可见该事件，则该见证人著名
                        if (voteYes.get() > (2 * this.numMembers / 3)) {
                            event.setIsFamous(true);
                            break;
                        // 超过2/3的见证人都不强可见该事件，则该见证人不著名
                        }else if (voteNo.get() > (2 * this.numMembers / 3)) {
                            event.setIsFamous(false);
                        }
//                        else {
//                            // 在r的后两轮无法确定 见证人的声望
//                            // 在r之后的第一个coin round 进行投票， 然后在 coin round后一轮进行选举
//                            int coinRound;
//                            for (int t = 0; t < 5; t++) {
//                                coinRound = (r / 10 + 1) * 10 + t * 10;
//
//                            }
//                        }
                    }
                }
            }
        });
    }


    /**
     * 为每个事件找出共识时间戳，接收轮次
     */
    public void findOrder() {
        // 获得所有已经全部确定声望的见证人轮次
        List<Integer> rs = allWitnessFamousDecideRounds();
        // log.debug("事件定序：allWitnessFamousDecideRounds：{}", rs);
        // 遍历hashgraph，
        // 如果一个轮次的所有著名见证人可见该事件，那么确定该事件的接收轮次
        this.hashgraph.forEach((id, chain)->{
            chain.forEach(e->{
                // 找到存在接收轮次的事件
                for (Integer round : rs) {
                    if (isReceiveRound(e, this.witnessMap.get(round), round)) {
                        break;
                    }
                }
                // 如果事件找到了接受轮次，那么确定共识时间戳
                if(e.getReceivedRound() != Integer.MIN_VALUE) {
                    getConsensusTimestamp(e);
                }
            });
        });
    }

    public boolean allWitnessFamousDecide(int r) {
        List<Event> es = witnessMap.get(r);
        for (Event item: es) {
            if (item.getIsFamous() == null) {
                return false;
            }
        }
        return true;
    }

    public List<Integer> allWitnessFamousDecideRounds() {
        List<Integer> rs =  new ArrayList<>();
        for (int i = 1; i < maxRound; i++) {
            if (allWitnessFamousDecide(i)) {
                rs.add(i);
            }
        }
        return rs;
    }

    /**
     * 判断事件src是否强可见事件dest
     * @param src
     * @param dest
     * @return 是否强可见
     */
    private boolean isStronglySee(Event src, Event dest) {
        AtomicBoolean res = new AtomicBoolean(false);

        int srcIdx = this.graph.getIdxByEvent(src);
        int destIdx = this.graph.getIdxByEvent(dest);
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

    private boolean isSee(Event src, Event dest) {
        int srcIdx = this.graph.getIdxByEvent(src);
        int destIdx = this.graph.getIdxByEvent(dest);
        List<List<Integer>> allPaths = graph.findAllPaths(srcIdx, destIdx);
        return !allPaths.isEmpty();
    }

    /**
     * 判断某一轮是否为一个事件的接受轮次
     * @param e 事件e
     * @param witnessList 该轮的所有见证者
     * @param witnessRound 该轮的编号
     * @return 判断结果
     */
    private boolean isReceiveRound(Event e, List<Event> witnessList, int witnessRound) {
        for (Event witness : witnessList) {
            if (witness.getIsFamous() && !isSee(witness, e)) {
                return false;
            }
        }
        e.setReceivedRound(witnessRound);
        return true;
    }

    // 获得共识时间戳
    private void getConsensusTimestamp(Event e) {
        if (e.getReceivedRound() == Integer.MIN_VALUE) {
            return;
        }
        List<Event> witnessList = witnessMap.get(e.getReceivedRound());
        List<Event> prefixEventList  = new ArrayList<>();
        for (int i = 0; i < witnessList.size(); i++) {
            Event witness = witnessList.get(i);

            int srcIdx = this.graph.getIdxByEvent(e);
            int destIdx = this.graph.getIdxByEvent(witness);
            int prefixEventIdx = this.graph.getEventIndex(srcIdx, destIdx);
            int x = prefixEventIdx % this.numMembers;
            int y = prefixEventIdx  / this.numMembers;
            prefixEventList.add(this.hashgraph.get(x).get(y));
        }

        prefixEventList.sort((o1, o2) -> (int) (o1.getTimestamp() - o2.getTimestamp()));
        e.setConsensusTimestamp(prefixEventList.get(prefixEventList.size() / 2).getTimestamp());
    }

    public void traversal() {
        this.getHashgraph().forEach((id, chain)->{
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
