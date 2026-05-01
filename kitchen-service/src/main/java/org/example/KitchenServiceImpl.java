package org.example;

import com.gourmet.kitchen.KitchenServiceGrpc;
import com.gourmet.kitchen.RejectRequest;
import com.gourmet.kitchen.RejectResponse;
import com.gourmet.kitchen.TicketRequest;
import com.gourmet.kitchen.TicketResponse;
import io.grpc.stub.StreamObserver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class KitchenServiceImpl extends KitchenServiceGrpc.KitchenServiceImplBase {

    private Connection connect() throws Exception {
        String url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/kitchendb");
        String user = System.getenv().getOrDefault("DB_USER", "postgres");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "postgres");
        return DriverManager.getConnection(url, user, password);
    }

    public static void initDB() {
        int retries = 10;
        while (retries > 0) {
            try (Connection conn = new KitchenServiceImpl().connect()) {
                String sql = """
                    CREATE TABLE IF NOT EXISTS tickets (
                        order_id VARCHAR(255) PRIMARY KEY,
                        status VARCHAR(50) NOT NULL
                    )
                    """;
                conn.createStatement().execute(sql);
                System.out.println("[KitchenService] DB initialized");
                return;
            } catch (Exception e) {
                System.err.println("[KitchenService] DB not ready, retrying... (" + retries + ") " + e.getMessage());
                retries--;
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            }
        }
        System.err.println("[KitchenService] DB init failed after retries");
    }

    @Override
    public void createTicket(TicketRequest req, StreamObserver<TicketResponse> ro) {
        System.out.println("[KitchenService] Creating ticket for order " + req.getOrderId());
        try (Connection conn = connect()) {
            String sql = """
                    INSERT INTO tickets (order_id, status)
                    VALUES (?, 'CREATE_PENDING')
                    ON CONFLICT (order_id) DO UPDATE SET status = 'CREATE_PENDING'
                    """;
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, req.getOrderId());
            stmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("[KitchenService] DB error: " + e.getMessage());
        }
        ro.onNext(TicketResponse.newBuilder().setSuccess(true).build());
        ro.onCompleted();
    }

    @Override
    public void rejectTicket(RejectRequest req, StreamObserver<RejectResponse> ro) {
        System.out.println("[KitchenService] Rejecting ticket for order " + req.getOrderId());
        try (Connection conn = connect()) {
            String sql = "UPDATE tickets SET status = 'REJECTED' WHERE order_id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, req.getOrderId());
            stmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("[KitchenService] DB error: " + e.getMessage());
        }
        ro.onNext(RejectResponse.newBuilder().setAcknowledged(true).build());
        ro.onCompleted();
    }
}