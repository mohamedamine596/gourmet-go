package org.example;

import io.grpc.Server;
import io.grpc.ServerBuilder;

public class AccountingServer {
    public static void main(String[] args) throws Exception {
        AccountingServiceImpl.initDB();
        Server server = ServerBuilder.forPort(50053)
                .addService(new AccountingServiceImpl())
                .build().start();
        System.out.println("AccountingService listening on 50053");
        server.awaitTermination();
    }
}