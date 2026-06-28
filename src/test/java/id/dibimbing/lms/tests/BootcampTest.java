package id.dibimbing.lms.tests;

import id.dibimbing.lms.BaseTest;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.testng.Assert.*;

/**
 * Folder Postman: "7. Bootcamp".
 * Skenario read: bootcamps + countBootcamps; bootcampContents untuk bootcamp pertama.
 * Termasuk regresi kuirk: countBootcamps WAJIB diberi `param`.
 */
public class BootcampTest extends BaseTest {

    private String firstBootcampId;

    @BeforeClass(alwaysRun = true)
    public void login() {
        ensureLoggedIn();
    }

    @Test(description = "bootcamps (page 1) + countBootcamps (dengan param)")
    public void listBootcamps() {
        String q = "query Bootcamps($param: InputBaseQuery!) {"
                + "  bootcamps(param: $param) { id title startedAt finishedAt countAssignedUser }"
                + "  countBootcamps(param: $param) }";
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("page", 1);
        param.put("limit", 5);
        param.put("search", "");

        Response res = gql.post(q, Map.of("param", param));
        assertNoGraphQLErrors(res);
        res.then().body("data.countBootcamps", greaterThanOrEqualTo(0));
        firstBootcampId = res.jsonPath().getString("data.bootcamps[0].id");
    }

    @Test(description = "REGRESI: countBootcamps TANPA param harus error (kuirk server)")
    public void countBootcampsWithoutParamErrors() {
        // schema menandai param opsional, tapi resolver butuh param.search -> error.
        Response res = gql.post("query { countBootcamps }");
        res.then().statusCode(200);
        assertNotNull(res.jsonPath().get("errors"),
                "countBootcamps tanpa param seharusnya menghasilkan errors");
    }

    @Test(dependsOnMethods = "listBootcamps",
            description = "bootcampContents untuk bootcamp pertama (jika ada)")
    public void bootcampContents() {
        if (firstBootcampId == null) {
            throw new SkipException("Tidak ada bootcamp untuk diuji");
        }
        String q = "query Contents($bootcampId: String!, $param: InputBaseQuery!) {"
                + "  bootcampContents(bootcampId: $bootcampId, param: $param) {"
                + "    id title liveClassTime liveClassDuration } }";
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("page", 1);
        param.put("limit", 5);
        param.put("search", "");

        Response res = gql.post(q, Map.of("bootcampId", firstBootcampId, "param", param));
        assertNoGraphQLErrors(res);
        assertNotNull(res.jsonPath().getList("data.bootcampContents"));
    }
}
