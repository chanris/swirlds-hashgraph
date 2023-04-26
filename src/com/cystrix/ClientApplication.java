package com.cystrix;

import com.cystrix.client.HashgraphClient;

public class ClientApplication {
    public static void main(String[] args) {
        HashgraphClient client = new HashgraphClient(8080);
        client.start();
    }
}
