package org.example;

import com.gourmet.accounting.AccountingServiceGrpc;
import com.gourmet.accounting.AuthorizeRequest;
import com.gourmet.accounting.AuthorizeResponse;
import com.gourmet.kitchen.KitchenServiceGrpc;
import com.gourmet.kitchen.RejectRequest;
import com.gourmet.kitchen.TicketRequest;
import com.gourmet.kitchen.TicketResponse;
import com.gourmet.order.OrderServiceGrpc;
import com.gourmet.order.UpdateStatusRequest;
import com.sun.net.httpserver.HttpServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class OrderOrchestrator {

    static ManagedChannel orderChannel;
    static ManagedChannel kitchenChannel;
    static ManagedChannel accountingChannel;

    static OrderServiceGrpc.OrderServiceBlockingStub orderStub;
    static KitchenServiceGrpc.KitchenServiceBlockingStub kitchenStub;
    static AccountingServiceGrpc.AccountingServiceBlockingStub accountingStub;

    public static void main(String[] args) throws Exception {

        String orderHost      = System.getenv().getOrDefault("ORDER_SERVICE_HOST", "localhost");
        String kitchenHost    = System.getenv().getOrDefault("KITCHEN_SERVICE_HOST", "localhost");
        String accountingHost = System.getenv().getOrDefault("ACCOUNTING_SERVICE_HOST", "localhost");

        orderChannel = ManagedChannelBuilder.forTarget("dns:///" + orderHost + ":50051")
                .usePlaintext().build();
        kitchenChannel = ManagedChannelBuilder.forTarget("dns:///" + kitchenHost + ":50052")
                .usePlaintext().build();
        accountingChannel = ManagedChannelBuilder.forTarget("dns:///" + accountingHost + ":50053")
                .usePlaintext().build();

        orderStub      = OrderServiceGrpc.newBlockingStub(orderChannel);
        kitchenStub    = KitchenServiceGrpc.newBlockingStub(kitchenChannel);
        accountingStub = AccountingServiceGrpc.newBlockingStub(accountingChannel);

        // Start HTTP server on port 8080
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/order", exchange -> {
            // CORS headers
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // Read request body
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            // Parse orderId and amount from JSON: {"orderId":"order-1","amount":50}
            String orderId = body.split("\"orderId\"")[1].split("\"")[1];
            double amount  = Double.parseDouble(body.split("\"amount\"")[1].replaceAll("[^0-9.]", "").trim());

            String result = runSaga(orderId, amount);

            byte[] response = result.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            OutputStream os = exchange.getResponseBody();
            os.write(response);
            os.close();
        });

        server.start();
        System.out.println("OrderOrchestrator HTTP server listening on port 8080");
    }

    static String runSaga(String orderId, double amount) {
        try {
            // Step 1
            orderStub.updateStatus(UpdateStatusRequest.newBuilder()
                    .setOrderId(orderId).setStatus("APPROVAL_PENDING").build());

            // Step 2
            TicketResponse ticketResponse = kitchenStub.createTicket(
                    TicketRequest.newBuilder().setOrderId(orderId).build());

            if (!ticketResponse.getSuccess()) {
                return "{\"status\":\"REJECTED\",\"reason\":\"Kitchen failed\"}";
            }

            // Step 3
            AuthorizeResponse authResponse = accountingStub.authorizeCard(
                    AuthorizeRequest.newBuilder().setOrderId(orderId).setAmount(amount).build());

            if (authResponse.getAuthorized()) {
                // ✅ Happy path
                orderStub.updateStatus(UpdateStatusRequest.newBuilder()
                        .setOrderId(orderId).setStatus("APPROVED").build());
                System.out.println("✅ Order " + orderId + " APPROVED");
                return "{\"status\":\"APPROVED\",\"orderId\":\"" + orderId + "\"}";

            } else {
                // ❌ Compensation
                kitchenStub.rejectTicket(
                        RejectRequest.newBuilder().setOrderId(orderId).build());
                orderStub.updateStatus(UpdateStatusRequest.newBuilder()
                        .setOrderId(orderId).setStatus("REJECTED").build());
                System.out.println("❌ Order " + orderId + " REJECTED");
                return "{\"status\":\"REJECTED\",\"reason\":\"Payment failed\",\"orderId\":\"" + orderId + "\"}";
            }

        } catch (Exception e) {
            return "{\"status\":\"ERROR\",\"reason\":\"" + e.getMessage() + "\"}";
        }
    }
}