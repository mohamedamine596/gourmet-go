package org.example;

import com.gourmet.order.OrderServiceGrpc;
import com.gourmet.order.UpdateStatusRequest;
import com.gourmet.order.UpdateStatusResponse;
import io.grpc.stub.StreamObserver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class OrderServiceImpl extends OrderServiceGrpc.OrderServiceImplBase {

    private Connection connect() throws Exception {
        String url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/orderdb");
        String user = System.getenv().getOrDefault("DB_USER", "postgres");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "postgres");
        return DriverManager.getConnection(url, user, password);
    }

    public static void initDB() {
        int retries = 10;
        while (retries > 0) {
            try (Connection conn = new OrderServiceImpl().connect()) {
                String sql = """
                        CREATE TABLE IF NOT EXISTS orders (
                            order_id VARCHAR(255) PRIMARY KEY,
                            status VARCHAR(50) NOT NULL
                        )
                        """;
                conn.createStatement().execute(sql);
                System.out.println("[OrderService] DB initialized");
                return;
            } catch (Exception e) {
                System.err.println("[OrderService] DB not ready, retrying... (" + retries + ") " + e.getMessage());
                retries--;
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            }
        }
        System.err.println("[OrderService] DB init failed after retries");
    }

    @Override
    public void updateStatus(UpdateStatusRequest req, StreamObserver<UpdateStatusResponse> ro) {
        System.out.println("[OrderService] Order " + req.getOrderId() + " → " + req.getStatus());
        try (Connection conn = connect()) {
            String sql = """
                    INSERT INTO orders (order_id, status)
                    VALUES (?, ?)
                    ON CONFLICT (order_id) DO UPDATE SET status = EXCLUDED.status
                    """;
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, req.getOrderId());
            stmt.setString(2, req.getStatus());
            stmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("[OrderService] DB error: " + e.getMessage());
        }
        ro.onNext(UpdateStatusResponse.newBuilder().setAcknowledged(true).build());
        ro.onCompleted();
    }
}