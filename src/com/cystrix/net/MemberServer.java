package com.cystrix.net;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.cystrix.client.HashgraphClient;
import com.cystrix.consensus.HashgraphMember;
import com.cystrix.consensus.Tx;
import com.cystrix.consensus.hashview.Event;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 成员节点进程服务端
 */
@Slf4j
public class MemberServer {
    private HashgraphMember member;
    private boolean isRunning = true;
    private ExecutorService executor;
    private  int port;
    private CopyOnWriteArrayList<Tx> txList; // 收到的新交易
    private AtomicInteger senderId;

    public MemberServer(int port, int threadNum, HashgraphMember member) {
        this.member = member;
        this.executor = Executors.newFixedThreadPool(threadNum);
        this.port = port;
        this.txList = new CopyOnWriteArrayList();
        this.senderId = new AtomicInteger(Integer.MIN_VALUE);
    }
    public HashgraphMember getMember() {
        return member;
    }

    public void start() {
        new Thread(()->{
            log.info("node_id: {}, node_name: {}, node_port: {}", this.member.getId(), this.member.getName(), this.port);
            try {
                ServerSocket serverSocket = new ServerSocket(port);
                //log.info("[Server started]: nodeId: {}, nodeName: {}\n" , member.getId(), member.getName());
                int requestId = 1;
                // 等待其他成员节点的 gossip sync请求 或者 客户端发来的 交易请求
                while (isRunning) {
                    Socket clientSocket = serverSocket.accept();
                    executor.submit(new Task(requestId++, clientSocket, this.member, this.txList, this.senderId));
                }
                log.info("node_id:{} shutdown....", this.member.getId());
            }catch (Exception e) {
                e.printStackTrace();
            }
            executor.shutdown();
        }).start();

        // 周期性的进行gossip 同步
        new Thread(()->{
            try {
                autoSync();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    /**
     * 进行gossip 同步
     */
    public void autoSync() throws Exception {
        while (isRunning) {
            int time = new Random(System.currentTimeMillis() / (this.getMember().getId() +1) + this.getMember().getId()* 1000).nextInt(5000) + 3000;
            TimeUnit.SECONDS.MILLISECONDS.sleep(time); // 1~2秒钟同步一次
            if (senderId.get() != Integer.MIN_VALUE) {
                log.info("当前自己id{}正在通信中，取消同步请求", this.member.getId());
                continue;
            }

            int receiverId = (int)(Math.random() * this.member.getNumMembers());
            if (receiverId == this.member.getId()) {
                receiverId ++;
                receiverId %= this.member.getNumMembers();
            }
            // log.debug("node_id:{} 选择与 node_id:{} 通信", this.getMember().getId(), receiverId);
            HashgraphClient client = new HashgraphClient(8080 + receiverId);

            Request request = new Request(200, "prepare", ""+ this.member.getId());
            // 获得接收的同步prepare信息
            Response response = client.sendRequest(request);
            // log.debug("node_Id:{} 收到 node_id:{} 的同步消息: {}", this.member.getId(), receiverId , response);
            if (response.getCode() != 200) {
                log.warn("sender_id: {}, receiver_id: {} gossip sync failed: {}", this.member.getId(), receiverId, "prepare阶段失败");
                continue;
            }
            // 下标 成员Id, 内容: 平行链高度
            List<Integer> blockchainHeightMap = JSON.parseObject(response.getData().toString(), ArrayList.class);
            List<Event> eventList = new ArrayList<>();
            for (int i = 0 ; i < this.member.getNumMembers(); i++) {
                int len = this.getMember().getHashgraph().get(i).size();
                int gap = len -  blockchainHeightMap.get(i);
                if (gap > 0) {
                    eventList.addAll(this.member.getHashgraph().get(i).subList(blockchainHeightMap.get(i), len));
                }
            }

            // 自己发送 接收者不知道的事件
            Request sendEventReq = new Request();
            sendEventReq.setCode(200);
            sendEventReq.setMsg("receiveEvent");

            HashMap<String,String> res = new HashMap<>();
            res.put("senderId", this.getMember().getId().toString());
            res.put("eventList", JSONObject.toJSONString(eventList));
            sendEventReq.setData(JSONObject.toJSONString(res));
            Response tographResponse = client.sendRequest(sendEventReq);
            if (tographResponse.getCode() != 200) {
                log.warn("sender_id: {}, receiver_id: {} gossip sync failed: {}", this.member.getId(), receiverId, "发送事件失败");
                continue;
            }
            log.info("sender_id: {}, receiver_id: {} gossip sync success!", this.member.getId(), receiverId);
        }
    }

    public void close() {
        this.isRunning = false;
    }

    static class Task implements Runnable {
        private int taskId;
        private HashgraphMember member;
        private Socket clientSocket;
        private CopyOnWriteArrayList<Tx> txList;
        private BufferedReader reader;  // 获得请求数据
        private PrintWriter writer; // 写回响应数据
        private  AtomicInteger senderId; // 当前谁正在向自己进行gossip sync
        private  int maxRequestIdleTime = 60 * 2;

        public Task(int taskId, Socket clientSocket, HashgraphMember member, CopyOnWriteArrayList<Tx> txList,
                    AtomicInteger senderId) throws IOException {
            this.taskId = taskId;
            this.clientSocket = clientSocket;
            this.member = member;
            this.txList = txList;
            this.senderId = senderId;
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            writer = new PrintWriter(clientSocket.getOutputStream(), true);
        }

        void receiveEventHandle(Request request, Response response) {
            HashMap<String,String> map = JSONObject.parseObject(request.getData().toString(), HashMap.class);
            List<Event> addList = JSONArray.parseArray(map.get("eventList"), Event.class);
            int senderId = Integer.parseInt(map.get("senderId"));
            List<Event> addedList = new ArrayList<>();
            int size = addList.size();

            if (this.senderId.get() != senderId){
                this.senderId.set(Integer.MIN_VALUE);
                response.setCode(201);
                response.setMsg("FAILED");
                return;
            }

            // 添加
            if (addList.size() != 0) {
                for (int i  = 0; i < size; i++) {
                    for (int j = 0; j < addList.size(); j++) {
                        if (this.member.addEvent(addList.get(j))) {
                            addedList.add(addList.get(j));
                            addList.remove(j);
                            break;
                        }
                    }
                }
                if (addedList.size() != size) {
                    // 退膛，break
                    for (Event event : addedList) {
                        int x = Integer.parseInt(event.getSelfParentHash().split(",")[0]);
                        this.member.getHashgraph().get(x).remove(event);
                        this.member.getGraph().removeEdgeByEven(event);
                    }
                    response.setCode(201);
                    response.setMsg("FAILED");
                }else {
                    // 创建新事件
                    int lastIdx = this.txList.size() - 1; // 每次最多打包十个交易
                    log.debug("打包交易 当前收集到的交易数量:{}", this.txList.size());
                    lastIdx = Math.min(10, lastIdx);
                    Event e = new Event();
                    if (lastIdx >= 0) {
                        List<Tx> txList1 =  this.txList.subList(0, lastIdx);
                        for (Tx tx: txList1) {
                            this.txList.remove(tx);
                        }
                        e.setTxList(txList1);
                    }
                    e.setTimestamp(System.currentTimeMillis());
                    ConcurrentHashMap<Integer, List<Event>> hashgraph = this.member.getHashgraph();
                    int memberId = this.member.getId();
                    int len = hashgraph.get(memberId).size();
                    int otherSize = hashgraph.get(senderId).size();
                    int x = Integer.parseInt(hashgraph.get(memberId).get(len-1).getSelfParentHash().split(",")[0]);
                    int y = Integer.parseInt(hashgraph.get(memberId).get(len-1).getSelfParentHash().split(",")[1]) + 1;
                    int otherX = Integer.parseInt(hashgraph.get(senderId).get(otherSize-1).getSelfParentHash().split(",")[0]);
                    int otherY = Integer.parseInt(hashgraph.get(senderId).get(otherSize-1).getSelfParentHash().split(",")[1]) + 1;
                    e.setSelfParentHash(x + "," + y);
                    e.setOtherParentHash(otherX + "," + otherY);
                    if (this.member.addEvent(e)) {
                        response.setCode(200);
                        response.setMsg("SUCCESS");
                    }else {
                        response.setCode(201);
                        response.setMsg("FAILED");
                    }
                }
            }else {
                response.setCode(200);
                response.setMsg("SUCCESS");
            }
            this.senderId.set(Integer.MIN_VALUE); // 同步结束
        }




        /**
         * 一个节点准备向另一个节点开始同步，
         * 首先获得接收者hash视图中每条链的高度
         * @param request
         * @param response
         */
        void prepareHandle(Request request, Response response) {
            Integer senderId = Integer.parseInt(request.getData().toString());
            if (this.senderId.get() != Integer.MIN_VALUE) {
                log.debug("node_id:{},  sender_id:{}", this.member.getId(), this.senderId);
                response.setCode(201);
                response.setMsg("FAILED");
                return;
            }
            this.senderId.set(senderId);
            List<Integer> height = new ArrayList<>();
            for (int i = 0; i < member.getNumMembers(); i ++) {
                height.add(member.getHashgraph().get(i).size());
            }
            response.setCode(200);
            response.setData(JSONObject.toJSONString(height));
            response.setMsg("SUCCESS");
        }

        void newTxHandle(Request request ,Response response) {
            Tx tx = JSONObject.parseObject(request.getData(), Tx.class);
            this.txList.add(tx);
            response.setCode(200);
            response.setMsg("SUCCESS");
        }

        void hashgraphViewHandle(Request request, Response response) {
            response.setCode(200);
            response.setMsg("SUCCESS");
            response.setData(JSONObject.toJSONString(this.member.getHashgraph()));
        }

        @Override
        public void run() {
            try {
                AtomicInteger requestIdleTime = timer();
                while (requestIdleTime.get() < maxRequestIdleTime) {
                    if (clientSocket.getInputStream().available() > 0) { // 反复读取客户端输入是否有数据，有数据马上处理
                        timer().set(0);
                        Request request = getRequest(reader);
                        Response response = new Response();
                        String mapping = request.getMsg();
                        switch (mapping) {
                            case "prepare":
                                prepareHandle(request, response);
                                break;
                            case "receiveEvent":
                                receiveEventHandle(request, response);
                                break;
                            case "newTx":
                                newTxHandle(request, response);
                                break;
                            case "hashgraphView":
                                hashgraphViewHandle(request, response);
                                break;
                            default:
                                response.setCode(200);
                                response.setMsg("default");
                                break;
                        }

                        // 响应信息
                        writer.println(JSONObject.toJSONString(response));
                    }else {
                        TimeUnit.SECONDS.sleep(1);
                    }
                }
                closeChannel();
            }catch (Exception e) {
                e.printStackTrace();
                log.warn("处理请求时抛出异常: {}", e.getMessage());
            }
        }

        AtomicInteger timer() {
            AtomicInteger count = new AtomicInteger(0);
            new Thread(()->{
                while (count.get() < maxRequestIdleTime * 1.2) {
                    count.incrementAndGet();
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }).start();
            return count;
        }

        public Request getRequest(BufferedReader reader) throws IOException {
            String json = reader.readLine();
            Request request = JSONObject.parseObject(json, Request.class);
            return  request;
        }

        /**
         * 关闭与客户端的连接
         */
        void closeChannel() {
            try {
                reader.close();
                writer.close();
                clientSocket.close();
                log.debug("client: {} 已关闭 isClosed:{} ", clientSocket, clientSocket.isClosed());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public boolean isRunning() {
        return this.isRunning;
    }

}
