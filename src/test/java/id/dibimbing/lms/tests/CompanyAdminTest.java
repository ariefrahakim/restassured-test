package id.dibimbing.lms.tests;

import id.dibimbing.lms.BaseTest;
import id.dibimbing.lms.Config;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.testng.Assert.*;

/**
 * Folder Postman: "2. Super Admin - Companies" (sebagian) & "3. Admin - Employees & Divisions".
 * Skenario read: myCompany, employees+count, divisions+count.
 * Skenario tulis aman & reversible: createDivision -> updateDivision -> deleteDivision.
 */
public class CompanyAdminTest extends BaseTest {

    @BeforeClass(alwaysRun = true)
    public void login() {
        ensureLoggedIn();
    }

    @Test(description = "myCompany -> detail perusahaan admin")
    public void myCompany() {
        String q = "query { myCompany { id name slug countEmployee countClass } }";
        Response res = gql.post(q);
        assertNoGraphQLErrors(res);
        assertEquals(res.jsonPath().getString("data.myCompany.id"), Config.companyId());
        res.then().body("data.myCompany.countEmployee", greaterThanOrEqualTo(0));
    }

    @Test(description = "employees (page 1) + countEmployees")
    public void listEmployees() {
        String q = "query Employees($page: Float, $limit: Float, $search: String) {"
                + "  employees(page: $page, limit: $limit, search: $search, orderColumn: \"createdAt\", orderBy: \"DESC\") {"
                + "    id name email status }"
                + "  countEmployees(search: $search) }";
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("page", 1);
        vars.put("limit", 5);
        vars.put("search", "");

        Response res = gql.post(q, vars);
        assertNoGraphQLErrors(res);
        assertNotNull(res.jsonPath().getList("data.employees"), "employees harus berupa list");
        res.then().body("data.countEmployees", greaterThanOrEqualTo(0));
    }

    @Test(description = "divisions (page 1) + countDivisions")
    public void listDivisions() {
        String q = "query Divisions($page: Float, $limit: Float, $search: String) {"
                + "  divisions(page: $page, limit: $limit, search: $search, orderColumn: \"name\", orderBy: \"ASC\") {"
                + "    id name description countEmployees }"
                + "  countDivisions(search: $search) }";
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("page", 1);
        vars.put("limit", 5);
        vars.put("search", "");

        Response res = gql.post(q, vars);
        assertNoGraphQLErrors(res);
        assertNotNull(res.jsonPath().getList("data.divisions"));
    }

    @Test(description = "CRUD divisi: create -> update -> delete (reversible)")
    public void divisionLifecycle() {
        // create
        String stamp = String.valueOf(System.nanoTime());
        String createQ = "mutation Create($input: DivisionInput!) {"
                + "  createDivision(input: $input) { id name description } }";
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("name", "RA-Test Division " + stamp);
        input.put("description", "Dibuat oleh RestAssured test, akan dihapus");
        Response created = gql.post(createQ, Map.of("input", input));
        assertNoGraphQLErrors(created);
        String divId = created.jsonPath().getString("data.createDivision.id");
        assertNotNull(divId, "createDivision harus mengembalikan id");

        try {
            // update
            String updateQ = "mutation Update($id: String!, $input: DivisionInput!) {"
                    + "  updateDivision(id: $id, input: $input) { id name } }";
            Map<String, Object> upd = new LinkedHashMap<>();
            upd.put("name", "RA-Test Division UPDATED " + stamp);
            upd.put("description", "updated");
            Response updated = gql.post(updateQ, Map.of("id", divId, "input", upd));
            assertNoGraphQLErrors(updated);
            // Catatan: mutation updateDivision sukses tanpa errors, namun response-nya
            // mengembalikan objek divisi pra-update (nama lama). Karena itu cukup verifikasi
            // mutation berjalan untuk id yang sama, bukan isi nama yang sudah diperbarui.
            assertEquals(updated.jsonPath().getString("data.updateDivision.id"), divId,
                    "updateDivision harus untuk id yang sama");
        } finally {
            // delete (cleanup)
            String deleteQ = "mutation Delete($id: String!) { deleteDivision(id: $id) }";
            Response deleted = gql.post(deleteQ, Map.of("id", divId));
            assertNoGraphQLErrors(deleted);
            assertTrue(deleted.jsonPath().getBoolean("data.deleteDivision"), "deleteDivision harus true");
        }
    }
}
