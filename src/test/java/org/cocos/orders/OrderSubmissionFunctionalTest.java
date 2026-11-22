package org.cocos.orders;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import com.jayway.jsonpath.JsonPath;
import org.cocos.ApiClient;
import org.cocos.PostgresTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@Import(PostgresTestConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.jpa.properties.jakarta.persistence.lock.timeout=1000")
class OrderSubmissionFunctionalTest {

    private static final String MARKET_BUY_ONE = """
            {"instrumentId":47,"side":"BUY","type":"MARKET","quantity":1}""";

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DataSource dataSource;

    private ApiClient api;

    @BeforeEach
    void createClient() {
        api = new ApiClient(port);
    }

    @Test
    void persistsFilledAndRejectedOrdersAndUpdatesPortfolio() throws Exception {
        var filled = createOrder(1, MARKET_BUY_ONE);
        var rejected = createOrder(1, """
                {"instrumentId":47,"side":"SELL","type":"MARKET","quantity":999}""");

        assertThat(filled.statusCode()).isEqualTo(201);
        assertThat(rejected.statusCode()).isEqualTo(201);
        assertThat(field(filled, "$.status")).isEqualTo("FILLED");
        assertThat(field(rejected, "$.status")).isEqualTo("REJECTED");

        List<String> persisted = jdbc.sql("SELECT status FROM orders WHERE id IN (:filled, :rejected) ORDER BY id")
                .param("filled", field(filled, "$.id", Integer.class))
                .param("rejected", field(rejected, "$.id", Integer.class))
                .query(String.class).list();
        assertThat(persisted).containsExactly("FILLED", "REJECTED");

        var portfolio = api.get("/api/v1/users/1/portfolio");
        assertThat(portfolio.statusCode()).isEqualTo(200);
        assertThat(field(portfolio, "$.availableCash", Number.class).doubleValue()).isEqualTo(626574.15);
        assertThat(JsonPath.parse(portfolio.body()).<List<Integer>>read("$.positions[?(@.ticker == 'PAMP')].quantity"))
                .containsExactly(41);

        var stored = api.get(ordersPath(1) + "/" + field(filled, "$.id", Integer.class));
        assertThat(stored.statusCode()).isEqualTo(200);
        assertThat(field(stored, "$.status")).isEqualTo("FILLED");
    }

    @Test
    void serializesConcurrentOrdersForTheSameAccount() {
        insertCashIn(2, 500_000);

        String body = """
                {"instrumentId":47,"side":"BUY","type":"MARKET","quantity":300}""";
        var first = api.postAsync(ordersPath(2), body);
        var second = api.postAsync(ordersPath(2), body);
        var responses = CompletableFuture.allOf(first, second)
                .thenApply(ignored -> List.of(first.join(), second.join()))
                .join();

        assertThat(responses).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(201));
        assertThat(responses.stream().map(response -> field(response, "$.status")))
                .containsExactlyInAnyOrder("FILLED", "REJECTED");

        assertThat(countOrdersByStatus(2, 47)).containsEntry("FILLED", 1L).containsEntry("REJECTED", 1L).hasSize(2);
    }

    @Test
    void cancellationReleasesTheReservationForANewOrder() throws Exception {
        insertCashIn(3, 10_000);

        var pending = createOrder(3, """
                {"instrumentId":47,"side":"BUY","type":"LIMIT","quantity":50,"price":100}""");
        assertThat(pending.statusCode()).isEqualTo(201);
        assertThat(field(pending, "$.status")).isEqualTo("NEW");

        var cancellation = api.postWithoutBody(ordersPath(3) + "/" + field(pending, "$.id", Integer.class) + "/cancel");
        assertThat(cancellation.statusCode()).isEqualTo(200);
        assertThat(field(cancellation, "$.status")).isEqualTo("CANCELLED");

        var market = createOrder(3, """
                {"instrumentId":47,"side":"BUY","type":"MARKET","quantity":10}""");
        assertThat(market.statusCode()).isEqualTo(201);
        assertThat(field(market, "$.status")).isEqualTo("FILLED");
    }

    @Test
    void failsFastWhenTheAccountStaysLockedByAnotherTransaction() throws Exception {
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);

            try {
                holder.createStatement().execute("SELECT id FROM users WHERE id = 4 FOR UPDATE");

                long start = System.nanoTime();
                var blocked = createOrder(4, MARKET_BUY_ONE);
                long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

                assertThat(blocked.statusCode()).isEqualTo(409);
                assertThat(field(blocked, "$.code")).isEqualTo("ACCOUNT_BUSY");
                assertThat(elapsedMillis).isBetween(900L, 4_000L);
            } finally {
                holder.rollback();
            }
        }

        assertThat(createOrder(4, MARKET_BUY_ONE).statusCode()).isEqualTo(201);
    }

    private HttpResponse<String> createOrder(int userId, String body) throws Exception {
        return api.post(ordersPath(userId), body);
    }

    private String ordersPath(int userId) {
        return "/api/v1/users/" + userId + "/orders";
    }

    private String field(HttpResponse<String> response, String path) {
        return field(response, path, String.class);
    }

    private <T> T field(HttpResponse<String> response, String path, Class<T> type) {
        return JsonPath.parse(response.body()).read(path, type);
    }

    private void insertCashIn(int userId, int amount) {
        jdbc.sql("""
                INSERT INTO orders (instrumentid, userid, size, price, type, side, status, datetime)
                VALUES (66, :userId, :amount, NULL, NULL, 'CASH_IN', 'FILLED', CURRENT_TIMESTAMP)""")
                .param("userId", userId)
                .param("amount", amount)
                .update();
    }

    private Map<String, Long> countOrdersByStatus(int userId, int instrumentId) {
        return jdbc.sql("SELECT status, COUNT(*) AS total FROM orders WHERE userid = :userId "
                        + "AND instrumentid = :instrumentId GROUP BY status")
                .param("userId", userId)
                .param("instrumentId", instrumentId)
                .query((rows, index) -> Map.entry(rows.getString("status"), rows.getLong("total")))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
