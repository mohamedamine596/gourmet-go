package org.example;

import com.gourmet.accounting.AccountingServiceGrpc;
import com.gourmet.accounting.AuthorizeRequest;
import com.gourmet.accounting.AuthorizeResponse;
import io.grpc.stub.StreamObserver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class AccountingServiceImpl extends AccountingServiceGrpc.AccountingServiceImplBase {

    private Connection connect() throws Exception {
        String url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/accountingdb");
        String user = System.getenv().getOrDefault("DB_USER", "postgres");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "postgres");
        return DriverManager.getConnection(url, user, password);
    }

    public static void initDB() {
        int retries = 10;
        while (retries > 0) {
            try (Connection conn = new AccountingServiceImpl().connect()) {
                String sql = """
                    CREATE TABLE IF NOT EXISTS authorizations (
                        order_id VARCHAR(255) PRIMARY KEY,
                        amount DOUBLE PRECISION NOT NULL,
                        authorized BOOLEAN NOT NULL
                    )
                    """;
                conn.createStatement().execute(sql);
                System.out.println("[AccountingService] DB initialized");
                return;
            } catch (Exception e) {
                System.err.println("[AccountingService] DB not ready, retrying... (" + retries + ") " + e.getMessage());
                retries--;
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            }
        }
        System.err.println("[AccountingService] DB init failed after retries");
    }

    @Override
    public void authorizeCard(AuthorizeRequest req, StreamObserver<AuthorizeResponse> ro) {
        boolean authorized = req.getAmount() < 100;
        System.out.println("[AccountingService] Amount=" + req.getAmount() + " → authorized=" + authorized);
        try (Connection conn = connect()) {
            String sql = """
                    INSERT INTO authorizations (order_id, amount, authorized)
                    VALUES (?, ?, ?)
                    ON CONFLICT (order_id) DO UPDATE SET amount = EXCLUDED.amount, authorized = EXCLUDED.authorized
                    """;
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, req.getOrderId());
            stmt.setDouble(2, req.getAmount());
            stmt.setBoolean(3, authorized);
            stmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("[AccountingService] DB error: " + e.getMessage());
        }
        ro.onNext(AuthorizeResponse.newBuilder().setAuthorized(authorized).build());
        ro.onCompleted();
    }
}