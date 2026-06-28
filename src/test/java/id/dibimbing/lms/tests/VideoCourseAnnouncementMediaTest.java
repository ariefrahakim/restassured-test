package id.dibimbing.lms.tests;

import id.dibimbing.lms.BaseTest;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.testng.Assert.assertNotNull;

/**
 * Folder Postman: "6. Video Course", "8. Announcement", "10. Media".
 * Skenario read ringan untuk memastikan endpoint & sesi bekerja.
 */
public class VideoCourseAnnouncementMediaTest extends BaseTest {

    @BeforeClass(alwaysRun = true)
    public void login() {
        ensureLoggedIn();
    }

    @Test(description = "videoCourses (katalog) + countVideoCourses")
    public void videoCourses() {
        String q = "query VC($page: Float, $limit: Float, $search: String) {"
                + "  videoCourses(page: $page, limit: $limit, search: $search) { id title access avgRating }"
                + "  countVideoCourses(search: $search) }";
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("page", 1);
        vars.put("limit", 5);
        vars.put("search", "");

        Response res = gql.post(q, vars);
        skipIfNotAuthorized(res);
        assertNoGraphQLErrors(res);
        res.then().body("data.countVideoCourses", greaterThanOrEqualTo(0));
    }

    @Test(description = "announcements + countAnnouncement")
    public void announcements() {
        String q = "query Ann($page: Float, $limit: Float, $search: String) {"
                + "  announcements(page: $page, limit: $limit, search: $search, orderColumn: \"createdAt\", orderBy: \"DESC\") {"
                + "    id title isForAllEmployee createdAt }"
                + "  countAnnouncement(search: $search) }";
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("page", 1);
        vars.put("limit", 5);
        vars.put("search", "");

        Response res = gql.post(q, vars);
        skipIfNotAuthorized(res);
        assertNoGraphQLErrors(res);
        assertNotNull(res.jsonPath().getList("data.announcements"));
    }

    @Test(description = "availableStorage (kuota media)")
    public void availableStorage() {
        Response res = gql.post("query { availableStorage { max occupied available } }");
        skipIfNotAuthorized(res);
        assertNoGraphQLErrors(res);
        assertNotNull(res.jsonPath().get("data.availableStorage"));
    }
}
