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
    private static final ObjectMapper PRETTY = new ObjectMapper();
    static { PRETTY.findAndRegisterModules(); }

    /** Aktifkan log request/response GraphQL ke stdout. Default ON; off via -Dgql.log=false. */
    private static final boolean LOG_ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("gql.log", "true"));

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

        Response response = given()
                .auth().preemptive().basic(Config.basicUser(), Config.basicPass())
                .contentType("application/json")
                .accept("application/json")
                .cookies(sessionCookies)
                .body(json)
                .when()
                .post(Config.baseUrl());

        if (LOG_ENABLED) {
            logRoundTrip(query, variables, response);
        }
        return response;
    }

    private static void logRoundTrip(String query, Map<String, Object> variables, Response response) {
        String op = extractOperation(query);
        String varsStr = variables == null || variables.isEmpty() ? "{}" : redactSecrets(variables).toString();
        String body;
        try {
            Object json = MAPPER.readValue(response.asString(), Object.class);
            body = PRETTY.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception e) {
            body = response.asString();
        }
        System.out.println("\n[GraphQL] " + op
                + "  vars=" + varsStr
                + "  status=" + response.getStatusCode()
                + "\n" + body + "\n");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> redactSecrets(Map<String, Object> vars) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : vars.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (k != null && k.toLowerCase().contains("password")) {
                out.put(k, "***");
            } else if (v instanceof Map) {
                out.put(k, redactSecrets((Map<String, Object>) v));
            } else {
                out.put(k, v);
            }
        }
        return out;
    }

    private static String extractOperation(String query) {
        // Ambil operation type + name dari baris awal (mis. "mutation createEmployee(...)").
        String q = query.trim();
        int newline = q.indexOf('\n');
        String head = newline > 0 ? q.substring(0, newline).trim() : q;
        int brace = head.indexOf('{');
        if (brace > 0) head = head.substring(0, brace).trim();
        return head.isEmpty() ? "query" : head;
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
