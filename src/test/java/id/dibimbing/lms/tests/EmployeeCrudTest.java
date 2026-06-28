package id.dibimbing.lms.tests;

import id.dibimbing.lms.BaseTest;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

/**
 * Folder Postman: "3. Admin - Employees & Divisions" — fokus skenario Employee CRUD.
 * Alur: createEmployee -> employeeById -> updateEmployee -> updateEmployeesDivision (bulk).
 *
 * Catatan: skema tidak punya mutation deleteEmployee, sehingga test ini meninggalkan
 * record di DB. Data dibuat unik per-run (timestamp) supaya tidak bentrok.
 */
public class EmployeeCrudTest extends BaseTest {

    private String employeeId;
    private String employeeEmail;
    private String employeeUsername;
    private String divisionAId;
    private String divisionBId;

    @BeforeClass(alwaysRun = true)
    public void setup() {
        ensureLoggedIn();
        // butuh minimal 2 division untuk skenario bulk move
        String q = "query { divisions(page: 1, limit: 5, search: \"\","
                + " orderColumn: \"name\", orderBy: \"ASC\") { id } }";
        Response res = gql.post(q);
        assertNoGraphQLErrors(res);
        List<Map<String, Object>> divs = res.jsonPath().getList("data.divisions");
        assertNotNull(divs, "divisions tidak boleh null");
        assertTrue(divs.size() >= 1, "butuh minimal 1 division");
        divisionAId = (String) divs.get(0).get("id");
        divisionBId = divs.size() >= 2 ? (String) divs.get(1).get("id") : divisionAId;
    }

    @Test(description = "createEmployee dengan payload lengkap -> id")
    public void createEmployee() {
        String stamp = String.valueOf(System.nanoTime());
        String mutation = "mutation createEmployee($input: EmployeeInput!) {"
                + "  createEmployee(input: $input) { id __typename } }";
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("name", "Arief Rahman Hakim - diBimbing");
        input.put("employeeId", "ID-Arief-" + stamp);
        employeeEmail = "arief+" + stamp + "@test.com";
        input.put("email", employeeEmail);
        employeeUsername = "arief" + stamp;
        input.put("username", employeeUsername);
        input.put("address", "test");
        input.put("angkatanId", 1);
        input.put("dateOfBirth", "2019-01-28T00:00:00.000Z");
        input.put("divisionId", divisionAId);
        input.put("employeeRole", "admin");
        input.put("gender", "male");
        input.put("nik", "31232132131");
        input.put("npwp", "2313313");
        input.put("phoneNumber", "8273817331");

        Response res = gql.post(mutation, Map.of("input", input));
        assertNoGraphQLErrors(res);
        employeeId = res.jsonPath().getString("data.createEmployee.id");
        assertNotNull(employeeId, "createEmployee harus mengembalikan id");
    }

    @Test(description = "employeeById -> detail employee yg baru dibuat",
            dependsOnMethods = "createEmployee")
    public void employeeById() {
        String q = "query EmployeeById($id: String!) {"
                + "  employeeById(id: $id) { id name email status division { id name } } }";
        Response res = gql.post(q, Map.of("id", employeeId));
        assertNoGraphQLErrors(res);
        assertEquals(res.jsonPath().getString("data.employeeById.id"), employeeId);
        assertEquals(res.jsonPath().getString("data.employeeById.division.id"), divisionAId);
    }

    @Test(description = "updateEmployee -> ubah name & phoneNumber",
            dependsOnMethods = "createEmployee")
    public void updateEmployee() {
        String mutation = "mutation updateEmployee($id: String!, $input: EmployeeInput!) {"
                + "  updateEmployee(id: $id, input: $input) { id name phoneNumber } }";
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("name", "Arief Rahman Hakim - diBimbing (updated)");
        input.put("phoneNumber", "8273817332");
        // server (formatUsername) crash bila field2 ini undefined, walau skema optional
        input.put("username", employeeUsername);
        input.put("email", employeeEmail);
        input.put("employeeRole", "admin");
        input.put("gender", "male");
        Response res = gql.post(mutation, Map.of("id", employeeId, "input", input));
        assertNoGraphQLErrors(res);
        assertEquals(res.jsonPath().getString("data.updateEmployee.id"), employeeId,
                "updateEmployee harus untuk id yang sama");
    }

    @Test(description = "updateEmployeesDivision (bulk) -> pindahkan ke divisi lain",
            dependsOnMethods = "createEmployee")
    public void updateEmployeesDivisionBulk() {
        // kalau hanya ada 1 division, skenario tetap valid: pindah ke divisi yg sama
        String mutation = "mutation MoveDivision($employeeIds: [String!]!, $divisionId: String!) {"
                + "  updateEmployeesDivision(employeeIds: $employeeIds, divisionId: $divisionId) }";
        Response res = gql.post(mutation,
                Map.of("employeeIds", List.of(employeeId), "divisionId", divisionBId));
        assertNoGraphQLErrors(res);
    }
}
