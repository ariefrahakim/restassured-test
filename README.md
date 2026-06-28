# RestAssured Tests — LMS B2B diBimbing GraphQL

Test otomatis (Java + RestAssured + **TestNG**, build **Gradle**) yang mereplikasi
skenario pada koleksi Postman `LMS-B2B-diBimbing.postman_collection.json`.

## Prasyarat
- JDK 17
- Gradle wrapper sudah disertakan (`./gradlew`) — tidak perlu install Gradle manual

## Konfigurasi
Kredensial dibaca dari **`restassured-tests/.env`** (sudah ada di folder ini).
Format `KEY: value` atau `KEY=value`:

```env
BASE_URL: https://lmsb2b.do.dibimbing.id/graphql
USERNAME: b2bserveruser            # HTTP Basic Auth (lapis 1)
PASSWORD: ...
ADMIN: arwendymelyn@dibimbing.id   # akun login aplikasi (lapis 2)
PASSWORD_ADMIN: ...
COMPANY_ID: 811637b1-9989-4d45-a9f5-220c5f2354f7
COMPANY_SLUG: dibimbingqa
```

> `.env` **tidak** di-commit (lihat `.gitignore`). Salin `.env.example` → `.env`
> lalu isi kredensialnya. Di CI, nilai diambil dari **GitHub Secrets** (lihat
> `.github/workflows/tests.yml`).

Override per-run tanpa mengubah `.env`:
```bash
./gradlew test -DCOMPANY_ID=xxxx -DADMIN=other@mail.com -DPASSWORD_ADMIN=secret
# atau pakai file .env lain:
./gradlew test -Denv.file=/path/lain/.env
```

## Menjalankan
```bash
./gradlew test                       # seluruh suite (testng.xml)
./gradlew test --tests '*AuthTest'   # satu class
./gradlew test --rerun               # paksa jalan ulang (abaikan cache)
```
Laporan HTML: `build/reports/tests/test/index.html`.

## Arsitektur singkat
| File | Peran |
|------|-------|
| `Config.java` | Baca `.env` + override System property/env var |
| `GraphQLClient.java` | POST GraphQL; kelola **2 lapis auth** (Basic + session cookie `sid_b2b`) |
| `BaseTest.java` | Sesi login bersama 1x untuk seluruh suite + helper assertion |
| `tests/*.java` | Skenario per folder Postman |

## Pemetaan ke folder Postman
| Test class | Folder Postman |
|------------|----------------|
| `AuthTest` | 0. Health, 1. Auth |
| `CompanyAdminTest` | 2. Companies, 3. Employees & Divisions |
| `ProgramTest` | 4. Program / Onboarding |
| `LearnerTest` | 5. Learner |
| `VideoCourseAnnouncementMediaTest` | 6. Video Course, 8. Announcement, 10. Media |
| `BootcampTest` | 7. Bootcamp |
| `MentorGradingActivityLogTest` | 11. Mentor / Grading, 12. Activity Log |

## Catatan
- Mutation grading (menilai peserta) **dinonaktifkan** secara default (`enabled=false`)
  karena mengubah data. Aktifkan manual dengan id + input valid di lingkungan uji aman.
- `divisionLifecycle` membuat divisi sementara lalu **menghapusnya kembali** (reversible).
- Test menargetkan lingkungan **Bootcamp QA** yang berisi data dummy; assertion dibuat
  toleran (cek status 200, tidak ada `errors`, struktur data benar) bukan nilai tetap.
- Query super-admin-only (mis. `videoCourses`) otomatis **di-skip** bila akun uji
  hanya berperan `admin` (lihat `BaseTest.skipIfNotAuthorized`).
