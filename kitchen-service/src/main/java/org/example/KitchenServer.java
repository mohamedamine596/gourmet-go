package org.example;

import io.grpc.Server;
import io.grpc.ServerBuilder;

public class KitchenServer {
    public static void main(String[] args) throws Exception {
        KitchenServiceImpl.initDB();
        Server server = ServerBuilder.forPort(50052)
                .addService(new KitchenServiceImpl())
                .build().start();
        System.out.println("KitchenService listening on 50052");
        server.awaitTermination();
    }
}