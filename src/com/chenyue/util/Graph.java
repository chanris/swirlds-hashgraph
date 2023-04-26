package com.chenyue.util;

import com.chenyue.consensus.hashview.Event;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 只保留的事件之间的引用关系，用于达成共识
 * 有向无环图的节点序号是离散的，需要修改
 * [] 确定一个事件的轮次
 * [] 判断一个事件是否是见证人
 * [] 判断一个见证人是否著名
 * [] 判断一个事件是否达成共识，若达成共识确定接收轮次和共识时间戳
 * 若最大序号为14（2， 3）  3*4+2
 *
 */
@Slf4j
@Data
public class Graph {
    private int numNodes;  // 图中节点数
    private List<List<Integer>> adjList;  // 图的邻接表表示
    private int numMembers; // 成员节点数

    public Graph(ConcurrentHashMap<Integer, List<Event>> hashgraph, int numMembers) {
        this.numMembers = numMembers;
//        this.numNodes = getMaxIdx(hashgraph) + 1;
        this.numNodes = 5000; //TODO
        this.adjList = new ArrayList<>(numNodes);
        for (int i = 0; i < numNodes; i++) {
            adjList.add(new ArrayList<>());
        }

        // 根据hashgraph构造邻接矩阵
        hashgraph.forEach((id, chain)-> {
            for(int i = 0; i < chain.size(); i++) {
                if (i == 0) {
                    continue;
                }
                Event event = chain.get(i);
                int selfPX, selfPY, otherPX, otherPY, selfX, selfY;
                String selfParentCoordinate = event.getSelfParentHash();
                String otherParentCoordinate = event.getOtherParentHash();
                selfPX = Integer.parseInt(selfParentCoordinate.split(",")[0]);
                selfPY = Integer.parseInt(selfParentCoordinate.split(",")[1]);
                otherPX = Integer.parseInt(otherParentCoordinate.split(",")[0]);
                otherPY = Integer.parseInt(otherParentCoordinate.split(",")[1]);
                selfX = selfPX;
                selfY = selfPY + 1;
                this.addEdge((selfX + selfY * numMembers), (selfPX + selfPY * numMembers));
                this.addEdge((selfX + selfY * numMembers), (otherPX + otherPY * numMembers));
            }
        });
    }

    public int getMaxIdx(HashMap<Integer, List<Event>> hashgraph) {
        AtomicInteger coordinateY = new AtomicInteger();
        AtomicInteger maxIdx = new AtomicInteger();
        hashgraph.forEach((id, chain)->{
            if (chain.size() > coordinateY.get()) {
                coordinateY.set(chain.size());
                maxIdx.set(id + coordinateY.get() *  numMembers);
            }
        });
        return maxIdx.get();
    }

    public void addEdge(int src, int dest) {
        // log.debug("添加{} -> {}的边", src, dest);
        adjList.get(src).add(dest);
        //adjList.get(dest).add(src);  // 如果是无向图，需要加上这句
    }

    public void removeEdgeByEven(Event event) {
        int selfPX, selfPY, otherPX, otherPY;
        String selfParentCoordinate = event.getSelfParentHash();
        String otherParentCoordinate = event.getOtherParentHash();
        selfPX = Integer.parseInt(selfParentCoordinate.split(",")[0]);
        selfPY = Integer.parseInt(selfParentCoordinate.split(",")[1]);
        otherPX = Integer.parseInt(otherParentCoordinate.split(",")[0]);
        otherPY = Integer.parseInt(otherParentCoordinate.split(",")[1]);
        this.removeEdge(selfPX + numMembers * (selfPY + 1), selfPX + numMembers * selfPY);
        this.removeEdge(selfPX + numMembers * (selfPY + 1), otherPX + numMembers * otherPY);
    }

    public void removeEdge(int src, int dest) {
        log.debug("删除边{} ->{}的边",src, dest);
        adjList.get(src).remove(new Integer(dest));
    }

    public List<List<Integer>> findAllPaths(int startNode, int targetNode) {
        List<List<Integer>> allPaths = new ArrayList<>();
        List<Integer> currPath = new ArrayList<>();
        boolean[] visited = new boolean[numNodes];
        dfs(startNode, targetNode, visited, currPath, allPaths);
        return allPaths;
    }

    private void dfs(int currNode, int targetNode, boolean[] visited, List<Integer> currPath, List<List<Integer>> allPaths) {
        visited[currNode] = true;
        currPath.add(currNode);
        if (currNode == targetNode) {
            allPaths.add(new ArrayList<>(currPath));
        } else {
            for (int neighbor : adjList.get(currNode)) {
                if (!visited[neighbor]) {
                    dfs(neighbor, targetNode, visited, currPath, allPaths);
                }
            }
        }
        currPath.remove(currPath.size() - 1);
        visited[currNode] = false;
    }

    public static void main(String[] args) {

        ConcurrentHashMap<Integer, List<Event>> hashgraph = new ConcurrentHashMap<>();
        for (int i = 0; i < 4; i ++) {
            hashgraph.put(i, new ArrayList<Event>());
        }
        Event event0 = new Event();
        Event event1 = new Event();
        Event event2 = new Event();
        Event event3 = new Event();
        Event event4 = new Event();
        Event event5 = new Event();
        Event event6 = new Event();
        Event event7 = new Event();
        Event event8 = new Event();
        Event event9 = new Event();
        Event event10 = new Event();
        Event event12 = new Event();
        Event event14 = new Event();


        event4.setSelfParentHash("0,0"); // 0
        event4.setOtherParentHash("1,0"); // 1
        event5.setSelfParentHash("1,0");  // 1
        event5.setOtherParentHash("0,1"); // 4
        event6.setSelfParentHash("2,0");  // 2
        event6.setOtherParentHash("3,0"); // 3
        event7.setSelfParentHash("3,0");  // 3
        event7.setOtherParentHash("1,1");  // 5
        event8.setSelfParentHash("0,1");  // 4
        event8.setOtherParentHash("2,1"); //6
        event9.setSelfParentHash("1,1");  // 5
        event9.setOtherParentHash("2,2"); // 10
        event10.setSelfParentHash("2,1"); // 6
        event10.setOtherParentHash("3,1"); // 7
        event12.setSelfParentHash("0,2"); // 8
        event12.setOtherParentHash("2,3"); // 14
        event14.setSelfParentHash("2,2"); // 10
        event14.setOtherParentHash("1,2"); // 9


        hashgraph.get(0).add(event0);
        hashgraph.get(0).add(event4);
        hashgraph.get(0).add(event8);
        hashgraph.get(0).add(event12);
        hashgraph.get(1).add(event1);
        hashgraph.get(1).add(event5);
        hashgraph.get(1).add(event9);
        hashgraph.get(2).add(event2);
        hashgraph.get(2).add(event6);
        hashgraph.get(2).add(event10);
        hashgraph.get(2).add(event14);
        hashgraph.get(3).add(event3);
        hashgraph.get(3).add(event7);
        Graph g = new Graph(hashgraph, 4);

        int src = 12;
        int dest = 2;
        List<List<Integer>> allPaths = g.findAllPaths(src, dest);

        System.out.println("从节点 " + src + " 到节点 " + dest + " 的所有路径为：");
        for (List<Integer> path : allPaths) {
            for (int i = 0; i < path.size() - 1; i++) {
                System.out.print(path.get(i) + " -> ");
            }
            System.out.println(path.get(path.size() - 1));
        }
    }
}
