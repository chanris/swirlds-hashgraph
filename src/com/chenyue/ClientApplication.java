package com.chenyue;

import com.chenyue.client.HashgraphClient;

import java.util.ArrayList;
import java.util.List;

public class ClientApplication {
    public static void main(String[] args) {
        HashgraphClient client = new HashgraphClient(8080);
        client.start();
    }
}
