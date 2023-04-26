package com.cystrix.client;

import com.alibaba.fastjson2.JSONObject;
import com.cystrix.consensus.Tx;
import com.cystrix.net.Request;
import com.cystrix.net.Response;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

@Slf4j
public class HashgraphClient {
    private int port;
    private  Socket client;
    private  BufferedReader reader;
    private PrintWriter writer;

    public HashgraphClient(int port) {
        this.port = port;
        try {
            this.client = new Socket("localhost", this.port);
            this.reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            this.writer = new PrintWriter(client.getOutputStream(), true);
        }catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }
    }

    public HashgraphClient(Socket socket) {
        this.port = socket.getPort();
        try {
            this.client = socket;
            this.reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            this.writer = new PrintWriter(client.getOutputStream(), true);
        }catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }
    }
    public void start() {
        boolean isRunning = true;
        while (isRunning) {
            Scanner sc = new Scanner(System.in);
            String cmd = sc.nextLine();
            switch (cmd) {
                case "sendTx":
                    sendNewTx();
                    break;
                case "hashgraph":
                case "H":
                    getHashgraphView();
                    break;
                case "shutdown":
                    isRunning = false;
                    close();
                    break;
                default:
                    System.out.println("error cmd");
                    break;
            }
        }
    }


    public void close() {
        try {
            reader.close();
            writer.close();
            client.close();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void requestSync() {
        Request request = new Request();
        request.setCode(200);
        request.setMsg("isSyncing");
        try {
            Response response = sendRequest(request);
            System.out.println(response.getMsg());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendNewTx(){
        Tx tx = new Tx();
        tx.setTimestamp(System.currentTimeMillis());
        tx.setSender("0xaasdasdasdasd");
        tx.setSign("asdasdasdkjsfaojdas");
        tx.setReceiver("0x12391290jdasdasd");
        tx.setBalance(100);
        Request request = new Request();
        request.setCode(200);
        request.setMsg("newTx");
        try {
            request.setData(JSONObject.toJSONString(tx));
            Response response = sendRequest(request);
            System.out.println("提交新交易到hashgraph网络 response: "+ response.getCode());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void getHashgraphView() {
        Request request = new Request();
        request.setCode(200);
        request.setMsg("hashgraphView");
        try {
            Response response = sendRequest(request);
            System.out.println(response.getData().toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Response sendRequest(Request request) throws IOException, InterruptedException {
        writer.println(JSONObject.toJSONString(request));
        String json;
        int count = 0;
        while(count < 1000) {
            if (client.getInputStream().available() > 0) {
                json = reader.readLine();
                return JSONObject.parseObject(json, Response.class);
            }else {
                TimeUnit.MILLISECONDS.sleep(20);
                count++;
            }
        }
        Response response = new Response();
        response.setCode(201);
        return  response;
    }
}
