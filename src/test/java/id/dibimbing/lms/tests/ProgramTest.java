package id.dibimbing.lms.tests;

import id.dibimbing.lms.BaseTest;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.testng.Assert.*;

/**
 * Folder Postman: "4. Program / Onboarding".
 * Skenario read: programs + countPrograms; drill chaptersByProgramId bila ada program.
 */
public class ProgramTest extends BaseTest {

    @BeforeClass(alwaysRun = true)
    public void login() {
        ensureLoggedIn();
    }

    @Test(description = "programs (page 1) + countPrograms")
    public void listPrograms() {
        String q = "query Programs($page: Float, $limit: Float, $search: String) {"
                + "  programs(page: $page, limit: $limit, search: $search, orderColumn: \"createdAt\", orderBy: \"DESC\") {"
                + "    id title type countChapters countContents countAssignedEmployee }"
                + "  countPrograms(search: $search) }";
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("page", 1);
        vars.put("limit", 5);
        vars.put("search", "");

        Response res = gql.post(q, vars);
        assertNoGraphQLErrors(res);
        res.then().body("data.countPrograms", greaterThanOrEqualTo(0));
    }

    @Test(description = "chaptersByProgramId untuk program pertama (jika ada)")
    public void chaptersOfFirstProgram() {
        String listQ = "query { programs(page:1, limit:1, orderColumn:\"createdAt\", orderBy:\"DESC\") { id } }";
        Response list = gql.post(listQ);
        assertNoGraphQLErrors(list);
        String programId = list.jsonPath().getString("data.programs[0].id");
        if (programId == null) {
            throw new org.testng.SkipException("Tidak ada program untuk diuji chapter-nya");
        }
        String q = "query Chapters($programId: String!) {"
                + "  chaptersByProgramId(programId: $programId) { id title order countContents } }";
        Response res = gql.post(q, Map.of("programId", programId));
        assertNoGraphQLErrors(res);
        assertNotNull(res.jsonPath().getList("data.chaptersByProgramId"));
    }
}
