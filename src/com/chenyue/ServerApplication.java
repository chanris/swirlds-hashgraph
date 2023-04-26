package com.chenyue;

import com.chenyue.client.HashgraphClient;
import com.chenyue.consensus.HashgraphMember;
import com.chenyue.net.MemberServer;

import java.util.concurrent.TimeUnit;

public class ServerApplication {
    public static void main(String[] args) throws InterruptedException {
        // List<MemberServer> group = groupStartup(5);
        MemberServer server = new MemberServer(8080, 20, new HashgraphMember(0, "HashNode-0", 5));
        MemberServer server1 = new MemberServer(8081, 20, new HashgraphMember(1, "HashNode-1", 5));
        MemberServer server2 = new MemberServer(8082, 20, new HashgraphMember(2, "HashNode-2", 5));
        MemberServer server3 = new MemberServer(8083, 20, new HashgraphMember(3, "HashNode-3", 5));
        MemberServer server4 = new MemberServer(8084, 20, new HashgraphMember(4, "HashNode-4", 5));
//        MemberServer server3 = new MemberServer(8083, 5, new HashgraphMember(3, "HashNode-3", 4));
        server.start();
        server1.start();
        server2.start();
        server3.start();
        server4.start();
    }
}
