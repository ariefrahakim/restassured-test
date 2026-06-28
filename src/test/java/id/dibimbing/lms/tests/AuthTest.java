package id.dibimbing.lms.tests;

import id.dibimbing.lms.BaseTest;
import id.dibimbing.lms.Config;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.testng.Assert.*;

/**
 * Folder Postman: "0. Health" & "1. Auth".
 * Skenario: ping, login (sukses), login (gagal), me.
 */
public class AuthTest extends BaseTest {

    @Test(priority = 0, description = "ping -> pong (endpoint & basic auth hidup)")
    public void ping() {
        Response res = gql.post("query Ping { ping }");
        assertNoGraphQLErrors(res);
        assertEquals(res.jsonPath().getString("data.ping"), "pong");
    }

    @Test(priority = 1, description = "login dengan kredensial salah -> ada errors, user null")
    public void loginWithWrongPasswordReturnsError() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("companyId", Config.companyId());
        vars.put("usernameOrEmail", Config.adminEmail());
        vars.put("password", "password-salah-xxx");

        Response res = gql.post(LOGIN, vars);
        res.then().statusCode(200);
        assertNull(res.jsonPath().get("data.login.user"), "User harusnya null saat password salah");
        assertNotNull(res.jsonPath().get("data.login.errors"), "Harus ada errors saat password salah");
    }

    @Test(priority = 2, description = "login admin sukses & menyimpan session cookie")
    public void loginSuccess() {
        ensureLoggedIn();
        assertTrue(gql.hasSession(), "Session cookie sid_b2b harus tersimpan setelah login");
    }

    @Test(priority = 3, dependsOnMethods = "loginSuccess",
            description = "me -> data user yang sedang login")
    public void me() {
        String q = "query Me { me { id name email role status companyId } }";
        Response res = gql.post(q);
        assertNoGraphQLErrors(res);
        assertEquals(res.jsonPath().getString("data.me.email"), Config.adminEmail());
        assertEquals(res.jsonPath().getString("data.me.role"), "admin");
        assertEquals(res.jsonPath().getString("data.me.status"), "active");
    }
}
