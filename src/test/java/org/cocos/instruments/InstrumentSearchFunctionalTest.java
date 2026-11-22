package org.cocos.instruments;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
import org.cocos.ApiClient;
import org.cocos.PostgresTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@Import(PostgresTestConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InstrumentSearchFunctionalTest {

    @LocalServerPort
    int port;

    private ApiClient api;

    @BeforeEach
    void createClient() {
        api = new ApiClient(port);
    }

    static Stream<Arguments> searches() {
        return Stream.of(
                Arguments.of("bMa", List.of(31)),
                Arguments.of("macro", List.of(31)),
                Arguments.of("  MACRO  ", List.of(31)),
                Arguments.of("banco", List.of(22, 28, 31, 19)),
                Arguments.of("ARS", List.of()),
                Arguments.of("pesos", List.of()),
                Arguments.of("inexistente", List.of()),
                Arguments.of("%", List.of()),
                Arguments.of("_", List.of()),
                Arguments.of("!", List.of()),
                Arguments.of("' OR 1=1 --", List.of()));
    }

    @ParameterizedTest
    @MethodSource("searches")
    void searchesLiteralTickerOrName(String query, List<Integer> expectedIds) throws Exception {
        var response = get("?q=" + encode(query));
        assertThat(response.statusCode()).isEqualTo(200);
        var json = JsonPath.parse(response.body());
        List<Integer> ids = json.read("$.content[*].id");
        assertThat(ids).containsExactlyElementsOf(expectedIds);
        assertThat(json.read("$.page.size", Integer.class)).isEqualTo(20);
        assertThat(json.read("$.page.number", Integer.class)).isZero();
        List<Map<String, Object>> items = json.read("$.content");
        items.forEach(item -> {
            assertThat(item).containsOnlyKeys("id", "ticker", "name", "type");
            assertThat(item.get("type")).isEqualTo("ACCIONES");
        });
    }

    @Test
    void returnsTheRequestedPageAndTotalCount() throws Exception {
        var response = get("?q=banco&size=2&page=1");
        assertThat(response.statusCode()).isEqualTo(200);
        var json = JsonPath.parse(response.body());
        List<Integer> ids = json.read("$.content[*].id");
        assertThat(ids).containsExactly(31, 19);
        assertThat(json.read("$.page.size", Integer.class)).isEqualTo(2);
        assertThat(json.read("$.page.number", Integer.class)).isEqualTo(1);
        assertThat(json.read("$.page.totalElements", Integer.class)).isEqualTo(4);
        assertThat(json.read("$.page.totalPages", Integer.class)).isEqualTo(2);
        Map<String, Object> body = json.read("$");
        assertThat(body).containsOnlyKeys("content", "page");
    }

    @Test
    void returnsEmptyPageBeyondTheResults() throws Exception {
        var response = get("?q=banco&size=2&page=100");
        assertThat(response.statusCode()).isEqualTo(200);
        List<?> items = JsonPath.parse(response.body()).read("$.content");
        assertThat(items).isEmpty();
    }

    private HttpResponse<String> get(String queryString) throws Exception {
        return api.get("/api/v1/instruments" + queryString);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
