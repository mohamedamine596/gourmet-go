package org.example;

import io.grpc.Server;
import io.grpc.ServerBuilder;

public class OrderServer {
    public static void main(String[] args) throws Exception {
        OrderServiceImpl.initDB();
        Server server = ServerBuilder.forPort(50051)
                .addService(new OrderServiceImpl())
                .build().start();
        System.out.println("OrderService listening on 50051");
        server.awaitTermination();
    }
}