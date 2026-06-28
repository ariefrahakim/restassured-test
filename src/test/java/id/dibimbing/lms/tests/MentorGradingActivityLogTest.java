package id.dibimbing.lms.tests;

import id.dibimbing.lms.BaseTest;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.testng.Assert.assertNotNull;

/**
 * Folder Postman: "11. Mentor / Grading" & "12. Activity Log".
 *
 * Skenario read aman:
 *  - Activity Log: userActivityLogByCompanyId + count.
 *  - Mentor: mentorBootcamp + count, assignedMentor/unassignedMentor untuk bootcamp pertama.
 *
 * Mutation grading (essayGrading, gradeUserSubmission, quizFinishGradingToggle, dst.)
 * BERSIFAT MENGUBAH DATA NILAI PESERTA -> TIDAK dieksekusi otomatis di sini.
 * Query string-nya tetap didokumentasikan sebagai konstanta + 1 test ber-Skip
 * agar mudah diaktifkan saat ada data uji yang aman.
 */
public class MentorGradingActivityLogTest extends BaseTest {

    @BeforeClass(alwaysRun = true)
    public void login() {
        ensureLoggedIn();
    }

    // ---------- Activity Log ----------
    @Test(description = "userActivityLogByCompanyId + count (input wajib)")
    public void activityLogByCompany() {
        String q = "query Log($input: InputBaseQuery!) {"
                + "  userActivityLogByCompanyId(input: $input) { id }"
                + "  countUserActivityLogByCompanyId(input: $input) }";
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("page", 1);
        input.put("limit", 5);
        input.put("search", "");

        Response res = gql.post(q, Map.of("input", input));
        assertNoGraphQLErrors(res);
        res.then().body("data.countUserActivityLogByCompanyId", greaterThanOrEqualTo(0));
        assertNotNull(res.jsonPath().getList("data.userActivityLogByCompanyId"));
    }

    // ---------- Mentor ----------
    @Test(description = "mentorBootcamp + countMentorBootcamp")
    public void mentorBootcamp() {
        String q = "query Mentor($query: InputBaseQuery!) {"
                + "  mentorBootcamp(query: $query) { id title }"
                + "  countMentorBootcamp(query: $query) }";
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", 1);
        query.put("limit", 5);
        query.put("search", "");

        Response res = gql.post(q, Map.of("query", query));
        assertNoGraphQLErrors(res);
        res.then().body("data.countMentorBootcamp", greaterThanOrEqualTo(0));
    }

    @Test(description = "assignedMentor & unassignedMentor untuk bootcamp pertama (jika ada)")
    public void mentorsOfFirstBootcamp() {
        String listQ = "query { bootcamps(param:{page:1,limit:1,search:\"\"}) { id } }";
        Response list = gql.post(listQ);
        assertNoGraphQLErrors(list);
        String bootcampId = list.jsonPath().getString("data.bootcamps[0].id");
        if (bootcampId == null) {
            throw new SkipException("Tidak ada bootcamp untuk diuji mentornya");
        }

        String q = "query Mentors($bootcampId: String!, $query: UserBaseQuery) {"
                + "  assignedMentor(bootcampId: $bootcampId, query: $query) { id name }"
                + "  countAssignedMentor(bootcampId: $bootcampId, query: $query)"
                + "  unassignedMentor(bootcampId: $bootcampId, query: $query) { id name }"
                + "  countUnassignedMentor(bootcampId: $bootcampId, query: $query) }";
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", 1);
        query.put("limit", 5);
        query.put("search", "");

        Response res = gql.post(q, Map.of("bootcampId", bootcampId, "query", query));
        assertNoGraphQLErrors(res);
        assertNotNull(res.jsonPath().getList("data.assignedMentor"));
        assertNotNull(res.jsonPath().getList("data.unassignedMentor"));
    }

    // ---------- Grading (didokumentasikan, tidak dieksekusi) ----------
    /** essayGrading(id, input: BootcampEssayGradingInput!) -> menilai jawaban esai quiz. */
    static final String ESSAY_GRADING =
            "mutation EssayGrading($id: String!, $input: BootcampEssayGradingInput!) {"
            + "  essayGrading(id: $id, input: $input) { id score } }";

    /** gradeUserSubmission(id, input: UserSubmissionUpdate!) -> menilai submission/project. */
    static final String GRADE_SUBMISSION =
            "mutation GradeSubmission($id: String!, $input: UserSubmissionUpdate!) {"
            + "  gradeUserSubmission(id: $id, input: $input) { id } }";

    @Test(enabled = false,
            description = "Grading mutation: aktifkan manual dgn quizLogId/submissionLogId valid")
    public void gradingMutationsDisabledByDefault() {
        throw new SkipException("Sengaja dinonaktifkan: mutation grading mengubah nilai peserta. "
                + "Isi id + input valid lalu set enabled=true untuk menjalankan di data uji aman.");
    }
}
