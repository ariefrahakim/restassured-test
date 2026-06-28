package id.dibimbing.lms;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.testng.annotations.BeforeSuite;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * Base class untuk semua test:
 *  - menyediakan GraphQLClient bersama (1 sesi login dipakai lintas test class)
 *  - helper login admin + assertion GraphQL umum.
 *
 * Catatan flow (lihat GRAPHQL_API_DOCUMENTATION.md bagian 4.1):
 *  login(companyId, usernameOrEmail, password) -> Set-Cookie sid_b2b -> request lain.
 */
public abstract class BaseTest {

    /** Dibagikan 1 instance untuk seluruh suite agar sesi login tetap hidup. */
    protected static final GraphQLClient gql = new GraphQLClient();

    @BeforeSuite(alwaysRun = true)
    public void setupSuite() {
        RestAssured.urlEncodingEnabled = false; // baseURL sudah lengkap, jangan di-encode
    }

    /** Login sebagai admin perusahaan; idempotent (skip bila sudah ada sesi). */
    protected synchronized void ensureLoggedIn() {
        if (gql.hasSession()) {
            return;
        }
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("companyId", Config.companyId());
        vars.put("usernameOrEmail", Config.adminEmail());
        vars.put("password", Config.adminPassword());

        Response res = gql.post(LOGIN, vars);
        res.then().statusCode(200);

        Object errors = res.jsonPath().get("data.login.errors");
        assertNull(errors, "Login gagal, errors=" + errors
                + " | pastikan ADMIN/PASSWORD_ADMIN/COMPANY_ID di .env benar");
        String userId = res.jsonPath().getString("data.login.user.id");
        assertNotNull(userId, "Login tidak mengembalikan user.id");

        gql.captureSession(res);
    }

    /** Assert tidak ada blok errors top-level pada response GraphQL. */
    protected void assertNoGraphQLErrors(Response res) {
        res.then().statusCode(200);
        Object errors = res.jsonPath().get("errors");
        assertNull(errors, "Response GraphQL mengandung errors: " + errors);
    }

    /**
     * Lewati (skip) test bila API menolak karena hak akses kurang
     * (mis. "user is not superadmin" / "not authenticated").
     * Akun QA default berperan `admin`, bukan `superadmin`, sehingga sebagian
     * query super-admin-only memang tidak bisa diakses — itu kondisi lingkungan,
     * bukan kegagalan logika test. Panggil sebelum assertNoGraphQLErrors.
     */
    protected void skipIfNotAuthorized(Response res) {
        Object errors = res.jsonPath().get("errors");
        if (errors == null) {
            return;
        }
        String msg = String.valueOf(errors).toLowerCase();
        if (msg.contains("superadmin") || msg.contains("not authenticated")
                || msg.contains("not authorized") || msg.contains("forbidden")) {
            throw new org.testng.SkipException(
                    "Dilewati: akun uji tidak punya hak akses untuk operasi ini -> " + errors);
        }
    }

    // ====== Operasi GraphQL yang dipakai berbagai test ======
    protected static final String LOGIN =
            "mutation Login($companyId: String!, $usernameOrEmail: String!, $password: String!) {"
            + "  login(companyId: $companyId, usernameOrEmail: $usernameOrEmail, password: $password) {"
            + "    errors { field message }"
            + "    user { id name email role status companyId company { id name slug } }"
            + "  }"
            + "}";
}
