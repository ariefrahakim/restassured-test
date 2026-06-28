package id.dibimbing.lms;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.response.Response;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Klien GraphQL tipis di atas RestAssured.
 *
 * Menangani 2 lapis autentikasi:
 *  - Lapis 1: HTTP Basic Auth (server gateway) -> selalu dikirim.
 *  - Lapis 2: session cookie "sid_b2b" hasil mutation login -> disimpan & dikirim ulang.
 */
public class GraphQLClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Cookie sesi (sid_b2b dll) yang dibagikan antar request setelah login. */
    private final Map<String, String> sessionCookies = new HashMap<>();

    /** Kirim query/mutation GraphQL beserta variables (boleh null). */
    public Response post(String query, Map<String, Object> variables) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("variables", variables == null ? new HashMap<>() : variables);

        String json;
        try {
            json = MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Gagal serialisasi body GraphQL", e);
        }

        return given()
                .auth().preemptive().basic(Config.basicUser(), Config.basicPass())
                .contentType("application/json")
                .accept("application/json")
                .cookies(sessionCookies)
                .body(json)
                .when()
                .post(Config.baseUrl());
    }

    public Response post(String query) {
        return post(query, null);
    }

    /** Simpan cookie sesi dari response login. */
    public void captureSession(Response response) {
        Map<String, String> cookies = response.getCookies();
        if (cookies != null) {
            cookies.forEach((k, v) -> {
                // simpan cookie sesi aplikasi & abaikan cookie cloudflare bot management
                if (!k.equalsIgnoreCase("__cf_bm")) {
                    sessionCookies.put(k, v);
                }
            });
        }
    }

    public boolean hasSession() {
        return sessionCookies.containsKey("sid_b2b");
    }

    public Map<String, String> sessionCookies() {
        return sessionCookies;
    }
}
