package org.cocos.portfolio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.cocos.ApiClient;
import org.cocos.PostgresTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@Import(PostgresTestConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PortfolioFunctionalTest {

    @LocalServerPort
    int port;

    private ApiClient api;

    @BeforeEach
    void createClient() {
        api = new ApiClient(port);
    }

    @Test
    void valuesTheProvidedHistoryOfUserOne() throws Exception {
        var json = portfolio(1);

        assertThat(json.<Map<String, Object>>read("$"))
                .containsOnlyKeys("availableCash", "totalAccountValue", "positions");
        assertThat(json.read("$.availableCash", Double.class)).isEqualTo(627500.00);
        assertThat(json.<Object>read("$.totalAccountValue")).isNull();

        assertThat(json.<List<Map<String, Object>>>read("$.positions")).allSatisfy(position ->
                assertThat(position).containsOnlyKeys("instrumentId", "ticker", "quantity", "marketValue",
                        "totalReturnPercentage", "dailyReturnPercentage"));
        assertThat(json.<List<Integer>>read("$.positions[*].instrumentId")).containsExactly(31, 54, 47);
        assertThat(json.<List<String>>read("$.positions[*].ticker")).containsExactly("BMA", "METR", "PAMP");
        assertThat(json.<List<Integer>>read("$.positions[*].quantity")).containsExactly(-10, 500, 40);
        assertThat(json.<List<Double>>read("$.positions[*].marketValue"))
                .containsExactly(-15028.00, 114750.00, 37034.00);
        assertThat(json.<List<Double>>read("$.positions[*].totalReturnPercentage"))
                .containsExactly(null, -8.20, -0.45);
        assertThat(json.<List<Double>>read("$.positions[*].dailyReturnPercentage"))
                .containsExactly(-1.15, -1.08, 0.44);
    }

    @Test
    void returnsAnEmptyPortfolioForAUserWithoutHistory() throws Exception {
        var json = portfolio(2);

        assertThat(json.<Map<String, Object>>read("$"))
                .containsOnlyKeys("availableCash", "totalAccountValue", "positions");
        assertThat(json.read("$.availableCash", BigDecimal.class)).isEqualByComparingTo("0.00");
        assertThat(json.read("$.totalAccountValue", BigDecimal.class)).isEqualByComparingTo("0.00");
        assertThat(json.<List<Object>>read("$.positions")).isEmpty();
    }

    private DocumentContext portfolio(int userId) throws Exception {
        var response = api.get("/api/v1/users/" + userId + "/portfolio");
        assertThat(response.statusCode()).isEqualTo(200);
        return JsonPath.parse(response.body());
    }
}
