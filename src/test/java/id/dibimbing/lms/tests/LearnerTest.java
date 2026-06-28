package id.dibimbing.lms.tests;

import id.dibimbing.lms.BaseTest;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertNotNull;

/**
 * Folder Postman: "5. Learner".
 * Skenario read sisi pembelajar: myUserProgram (boleh kosong utk akun admin).
 */
public class LearnerTest extends BaseTest {

    @BeforeClass(alwaysRun = true)
    public void login() {
        ensureLoggedIn();
    }

    @Test(description = "myUserProgram + countMyUserProgram (list valid walau kosong)")
    public void myUserProgram() {
        String q = "query My($status: [String!]!, $type: [String!]!, $page: Float, $limit: Float, $search: String) {"
                + "  myUserProgram(status: $status, type: $type, page: $page, limit: $limit, search: $search) {"
                + "    id status isFinished countPercentageProgress program { id title type } }"
                + "  countMyUserProgram(status: $status, type: $type) }";
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("status", List.of("ASSIGNED", "ON_PROGRESS"));
        vars.put("type", List.of("ONBOARDING", "training"));
        vars.put("page", 1);
        vars.put("limit", 10);
        vars.put("search", "");

        Response res = gql.post(q, vars);
        assertNoGraphQLErrors(res);
        assertNotNull(res.jsonPath().getList("data.myUserProgram"),
                "myUserProgram harus berupa list (boleh kosong)");
    }
}
