# Dokumentasi GraphQL API — LMS B2B diBimbing

Dokumentasi ini menjelaskan endpoint GraphQL, kredensial, mekanisme autentikasi,
dan alur (flow) penggunaan API beserta contoh payload-nya.

> Sumber kredensial: file `.env` pada repo ini.
> Schema diperoleh langsung lewat **introspection** terhadap endpoint produksi
> (159 query + 159 mutation).

---

## 1. Koneksi & Kredensial

| Item | Nilai |
|------|-------|
| **Base URL / Endpoint** | `https://lmsb2b.do.dibimbing.id/graphql` |
| **Protocol** | GraphQL over HTTP `POST` |
| **Content-Type** | `application/json` |
| **HTTP Basic Auth user** | `b2bserveruser` |
| **HTTP Basic Auth pass** | `ENAcA3Sog22681sAKvih8KkpDKvF2aQ6` |
| **Server stack** | Apollo Server + TypeGraphQL (Express) di belakang Cloudflare |

Isi `.env`:

```env
baseURL: https://lmsb2b.do.dibimbing.id/graphql
username: b2bserveruser
password: ENAcA3Sog22681sAKvih8KkpDKvF2aQ6
```

### Catatan penting soal kredensial `.env`

Hasil pengujian langsung terhadap endpoint:

1. **Kredensial `b2bserveruser` adalah HTTP Basic Auth tingkat-gateway / server-to-server**,
   dikirim lewat header `Authorization: Basic <base64(user:pass)>`. Ini *bukan* akun login aplikasi.
2. Kredensial ini **tidak** bisa dipakai pada mutation `loginSuperAdmin` (diuji → `wrong username or password`).
3. **Autentikasi level aplikasi** (yang menentukan `me`, role, akses data) dilakukan terpisah
   lewat mutation `login` / `loginSuperAdmin`, yang menghasilkan **session cookie**.

Jadi ada **dua lapis** autentikasi:

```
[ Client ]
    │  Authorization: Basic (b2bserveruser:****)   ← lapis 1: akses server
    ▼
[ GraphQL Server ]
    │  mutation login(...) → Set-Cookie: session   ← lapis 2: identitas user
    ▼
[ Resolver dilindungi isAuth / isSuperAdmin ]
```

---

## 2. Anatomi Request

Semua operasi memakai bentuk yang sama:

```bash
curl -X POST 'https://lmsb2b.do.dibimbing.id/graphql' \
  -u 'b2bserveruser:ENAcA3Sog22681sAKvih8KkpDKvF2aQ6' \
  -H 'Content-Type: application/json' \
  -c cookies.txt -b cookies.txt \
  -d '{
        "query": "<operasi graphql>",
        "variables": { ... }
      }'
```

- `-u` → mengirim HTTP Basic Auth (lapis 1).
- `-c cookies.txt -b cookies.txt` → menyimpan & mengirim session cookie (lapis 2).
- Body JSON wajib punya field `query`; `variables` opsional.

Struktur **response** GraphQL standar:

```json
{ "data": { ... }, "errors": [ { "message": "...", "path": ["..."] } ] }
```

---

## 3. Tingkat Proteksi Resolver

Setiap resolver dijaga middleware. Diketahui dari pesan error introspeksi:

| Middleware | Arti | Contoh operasi |
|-----------|------|----------------|
| *(tanpa proteksi)* | publik | `ping` |
| `isAuth` | wajib login (role apapun) | `me`, `myUserProgram`, `myBootcamps` |
| `isSuperAdmin` | wajib role super admin | `companies`, `categories`, `createCompany` |
| (role admin perusahaan) | admin dari company terkait | `createEmployee`, `createProgram`, `assignProgram` |

Contoh error saat belum login:

```json
{ "errors": [ { "message": "not authenticated", "path": ["me"] } ], "data": null }
```

---

## 4. FLOW UTAMA

Operasi dikelompokkan berdasarkan **alur peran (persona)**:
Super Admin → Company Admin → Employee (Learner) → Mentor.

### 4.0 Healthcheck (publik)

```graphql
query { ping }      # → { "data": { "ping": "pong" } }
```

---

### 4.1 FLOW: Autentikasi & Sesi

```
login / loginSuperAdmin  ──►  Set-Cookie (session)  ──►  me  ──►  ... operasi lain ...  ──►  logout
```

#### a. Login Super Admin

```graphql
mutation LoginSuperAdmin($usernameOrEmail: String!, $password: String!) {
  loginSuperAdmin(usernameOrEmail: $usernameOrEmail, password: $password) {
    errors { field message }
    user { id email username role status companyId }
  }
}
```
Variables:
```json
{ "usernameOrEmail": "superadmin@dibimbing.id", "password": "********" }
```

#### b. Login User Perusahaan (admin / employee)

`login` butuh `companyId` — identifikasi perusahaan tempat user terdaftar.

```graphql
mutation Login($companyId: String!, $usernameOrEmail: String!, $password: String!) {
  login(companyId: $companyId, usernameOrEmail: $usernameOrEmail, password: $password) {
    errors { field message }
    user { id name email role status companyId company { id name slug } }
  }
}
```
Variables:
```json
{ "companyId": "<uuid-company>", "usernameOrEmail": "budi@perusahaan.com", "password": "********" }
```

> Login yang sukses mengembalikan `Set-Cookie` session. Cookie ini **wajib disertakan**
> di setiap request berikutnya (gunakan `-b cookies.txt`).

#### c. Verifikasi sesi aktif

```graphql
query Me {
  me {
    id name email username role status
    companyId company { id name slug }
    countAssignedProgram countAssignedBootcamp isMentor
  }
}
```

#### d. Operasi sesi lainnya

| Operasi | Tipe | Payload |
|---------|------|---------|
| `logout` | mutation | `mutation { logout }` |
| `forgotPassword` | mutation | `forgotPassword(email, slug, captcha)` |
| `changePassword` | mutation | `changePassword(token, newPassword)` |
| `updateMyPassword` | mutation | input `UpdateMyPasswordInput { password, confirmPassword, currentPassword }` |
| `updateMyProfile` | mutation | input `EmployeeInput` |

---

### 4.2 FLOW: Super Admin — Kelola Perusahaan (`isSuperAdmin`)

```
companies (list) ──► createCompany ──► updateCompany ──► activate/inactivate
                                  └──► atur kuota video course
```

#### Lihat & cari perusahaan
```graphql
query Companies($page: Float, $limit: Float, $search: String, $status: [String!]) {
  companies(page: $page, limit: $limit, search: $search, status: $status,
            orderColumn: "createdAt", orderBy: "DESC") {
    id name slug email status maxActiveEmployee maxStorage
    countEmployee countActiveEmployee countVideoCourse countClass
  }
  countCompanies(search: $search, status: $status)
}
```

#### Buat perusahaan + PIC sekaligus
```graphql
mutation CreateCompany($input: CreateCompanyParam!) {
  createCompany(input: $input) { id name slug status }
}
```
Payload `variables` (perhatikan `CreateCompanyParam` = `company` + `pic`):
```json
{
  "input": {
    "company": {
      "name": "PT Maju Jaya",
      "email": "hr@majujaya.com",
      "phoneNumber": "0218888888",
      "address": "Jakarta",
      "slug": "maju-jaya",
      "prefix": "MJ",
      "maxActiveEmployee": 100,
      "maxStorage": 5000,
      "mediaTypeOptions": ["video", "document"],
      "themeId": "<uuid-theme>"
    },
    "pic": {
      "name": "Admin HR",
      "email": "admin@majujaya.com",
      "username": "adminmajujaya",
      "employeeRole": "ADMIN",
      "phoneNumber": "08123456789"
    }
  }
}
```

#### Operasi Super Admin lainnya

| Tujuan | Operasi | Catatan payload |
|--------|---------|-----------------|
| Buat company (profil saja) | `createNewCompany(input: CompanyProfileInput!)` | tanpa PIC |
| Update company | `updateCompany(id, input: CreateCompanyParam!)` | |
| Update profil company | `updateNewCompany(id, input: CompanyProfileInput!)` | |
| Aktif / non-aktif | `activateCompany(id)` / `inactivateCompany(id)` | |
| Ubah slug | `updateCompanySlug(id, newSlug)` | |
| Kuota video course | `updateCompanyVideoCoursePermission(id, input: CompanyVideoCoursePermissionInput!)` | `{ maxVideoCourse, videoCourseExpiredDate }` |
| Toggle izin video course | `toggleCompanyCanAssignedVideoCourse(id)` | |
| Kelola admin company | `createAdmin(companyId, input: EmployeeInput!)`, `updateAdmin`, `deleteAdmin`, `admins`, `adminById` | |
| Kategori & video course master | `categories`, `videoCourses`, `videoCourse(id)` | |
| Tes SMTP | `testSMTPEmail(receiverEmail, input: InputSMTPData!)` | |

---

### 4.3 FLOW: Company Admin — Karyawan & Divisi

```
divisions ──► createDivision
employees ──► createEmployee ──► (assign program/bootcamp) ──► activate/inactivate
          └──► importDataEmployee (bulk) / exportDataEmployees
```

#### Daftar & hitung karyawan
```graphql
query Employees($page: Float, $limit: Float, $search: String,
                $status: [String!], $divisionIds: [String!]) {
  employees(page: $page, limit: $limit, search: $search,
            status: $status, divisionIds: $divisionIds,
            orderColumn: "name", orderBy: "ASC") {
    id name email employeeId status divisionId
    division { id name } angkatan { id name }
    countAssignedProgram countFinishedProgram averageTotalScore
  }
  countEmployees(search: $search, status: $status, divisionIds: $divisionIds)
}
```

#### Buat karyawan
```graphql
mutation CreateEmployee($input: EmployeeInput!) {
  createEmployee(input: $input) { id name email username status }
}
```
Payload:
```json
{
  "input": {
    "name": "Siti Aminah",
    "employeeId": "EMP-001",
    "email": "siti@majujaya.com",
    "username": "sitiaminah",
    "phoneNumber": "08111111111",
    "gender": "FEMALE",
    "dateOfBirth": "1995-04-10T00:00:00.000Z",
    "address": "Bandung",
    "divisionId": "<uuid-division>",
    "employeeRole": "EMPLOYEE",
    "angkatanId": 1,
    "nik": "3273xxxxxxxx",
    "npwp": "09.xxx.xxx.x-xxx.xxx"
  }
}
```

#### Operasi karyawan & divisi lainnya

| Tujuan | Operasi | Payload kunci |
|--------|---------|---------------|
| Update karyawan | `updateEmployee(id, input: EmployeeInput!)` | |
| Aktif / non-aktif | `activateEmployee(id)` / `inactivateEmployee(id)` | |
| Hapus | `deleteEmployee(id)` | |
| Reset password (admin) | `updatePasswordEmployee(id, input: UpdatePasswordInput!)` | `{ password, confirmPassword, adminPassword }` |
| Kirim ulang email password | `resendEmployeePasswordEmail(employeeId)` | |
| Pindah divisi (bulk) | `updateEmployeesDivision(employeeIds: [..], divisionId)` | |
| Import dari file | `importDataEmployee(file: Upload!)` | multipart upload |
| Export | `exportDataEmployees(...)`, `exportEmployeeProgress(...)` | → URL file (String) |
| Divisi | `divisions`, `divisionById`, `createDivision`, `updateDivision`, `deleteDivision` | `DivisionInput { name, description }` |
| Angkatan | `angkatans`, `createAngkatan`, `updateAngkatan`, `deleteAngkatan` | `AngkatanInput { name, description }` |

---

### 4.4 FLOW: Program / Onboarding & Training (konten e-learning)

Hirarki konten: **Program → Chapter → Content → Question**.

```
createProgram ──► createChapter ──► createContent ──► createQuestion
        │                                  └──► (ReorderContent / ReorderChapter)
        └──► assignProgram (ke karyawan) ──► (employee mengerjakan) ──► report/export
```

#### 1) Buat Program
```graphql
mutation CreateProgram($input: ProgramInput!) {
  createProgram(input: $input) { id title type isSequential }
}
```
```json
{ "input": { "title": "Onboarding Karyawan Baru", "description": "Materi wajib",
             "type": "ONBOARDING", "isSequential": true } }
```

#### 2) Buat Chapter
```graphql
mutation CreateChapter($input: ChapterInput!) {
  createChapter(input: $input) { id title order }
}
```
```json
{ "input": { "title": "Bab 1 - Pengenalan", "description": "...",
             "order": 1, "programId": "<uuid-program>" } }
```

#### 3) Buat Content
```graphql
mutation CreateContent($input: ContentInput!) {
  createContent(input: $input) { id title type order }
}
```
```json
{ "input": {
    "title": "Video Sambutan CEO", "description": "...", "order": 1,
    "chapterId": "<uuid-chapter>", "type": "VIDEO",
    "mediaType": "video", "videoUrl": "https://...", "mediaId": "<uuid-media>",
    "duration": 600, "thumbnailUrl": "https://...",
    "attachmentUrl": ["https://.../file.pdf"]
} }
```
> Untuk content tipe kuis: set `type: "QUIZ"`, `isRandomQuestion`, `randomQuestionCount`,
> lalu tambahkan pertanyaan via `createQuestion`.

#### 4) Buat Question
```graphql
mutation CreateQuestion($input: QuestionInput!) {
  createQuestion(input: $input) { id question options answers }
}
```
```json
{ "input": {
    "contentId": "<uuid-content>", "order": 1, "type": "MULTIPLE_CHOICE",
    "question": "Apa visi perusahaan?",
    "options": ["A. ...", "B. ...", "C. ...", "D. ..."],
    "answers": [0]
} }
```

#### 5) Assign Program ke karyawan
```graphql
mutation AssignProgram($input: AssignProgramInput!) {
  assignProgram(input: $input)
}
```
```json
{ "input": {
    "programId": "<uuid-program>",
    "employeeIds": ["<uuid-user-1>", "<uuid-user-2>"],
    "startDate": "2026-07-01T00:00:00.000Z",
    "endDate": "2026-07-31T23:59:59.000Z"
} }
```

#### Operasi Program lainnya

| Tujuan | Operasi |
|--------|---------|
| List / detail program | `programs`, `programById(id)`, `countPrograms` |
| Update / hapus program | `updateProgram(id, input)`, `deleteProgram(id)` |
| Program dari video course | `createProgramByVideoCourseId(videoCourseId)` |
| Chapter/Content/Question CRUD | `updateChapter`/`deleteChapter`, `updateContent`/`deleteContent`, `updateQuestion`/`deleteQuestion` |
| Reorder | `ReorderChapter(params)`, `ReorderContent(params)`, `reorderBootcampQuestion` — input `[ReorderData{ id, order }]` |
| Lihat konten | `chaptersByProgramId`, `contentByChapterId`, `contentById`, `questionByContentId` |
| Siapa di-assign | `assignedEmployeeIds(programId)`, `userProgramByProgramId`, `countUserProgramByProgramId` |
| Ringkasan & export | `assignedProgramSummaryData`, `completedProgramSummaryData`, `totalWatchTimeSummary`, `exportProgramOverview`, `exportAssignedEmployee` |
| Ubah deadline / hapus assignment | `updateUserProgramEndDate(id, endDate, startDate)`, `deleteUserProgram(id)` |

---

### 4.5 FLOW: Employee (Learner) — Mengerjakan Program

```
me ──► myUserProgram ──► userProgressChaptersByUserProgramId ──► contentById
   ──► submitContentLog (tandai selesai) / submitTestLog (jawab kuis) ──► testResult...
```

#### Program saya
```graphql
query MyUserProgram($status: [String!]!, $type: [String!]!,
                    $page: Float, $limit: Float, $search: String) {
  myUserProgram(status: $status, type: $type, page: $page, limit: $limit, search: $search) {
    id status startDate endDate isFinished countPercentageProgress avgScore
    program { id title type countChapters countContents }
  }
  countMyUserProgram(status: $status, type: $type)
}
```
```json
{ "status": ["ASSIGNED", "ON_PROGRESS"], "type": ["ONBOARDING"], "page": 1, "limit": 10 }
```

#### Lihat progress & konten
```graphql
query Progress($userProgramId: String!) {
  userProgressChaptersByUserProgramId(userProgramId: $userProgramId) {
    id title order countContents countFinishedContent
    contents { id title type order isComplete }
  }
}
```

#### Tandai konten selesai (non-kuis)
```graphql
mutation SubmitContentLog($contentId: String!) {
  submitContentLog(contentId: $contentId) { id }
}
```

#### Submit jawaban kuis
```graphql
mutation SubmitTestLog($contentId: String!, $answers: [ContentLogAnswerInput!]!) {
  submitTestLog(contentId: $contentId, answers: $answers) {
    id  # ContentLog hasil
  }
}
```
```json
{
  "contentId": "<uuid-content-quiz>",
  "answers": [
    { "questionId": "<uuid-q1>", "answers": [0] },
    { "questionId": "<uuid-q2>", "answers": [1, 3] },
    { "questionId": "<uuid-q3>", "essayAnswer": "Jawaban esai saya..." }
  ]
}
```

#### Operasi Learner lainnya

| Tujuan | Operasi |
|--------|---------|
| Dashboard ringkas | `countActiveTraining`, `countFinishTraining`, `nearestTraining` |
| Hasil tes saya | `testResultByUserProgramId`, `testResultDetailByUserProgramId`, `myTestLog`, `testLogByUserProgramId` |
| Soal acak utk dikerjakan | `employeeQuestionByContentId(contentId, randomQuestionId)` |
| Lapor masalah konten | `createUserReportedProblem(input: UserReportedProblemInput!)` |

---

### 4.6 FLOW: Video Course (mandiri)

```
videoCourses / myVideoCourses ──► videoCourse(id) ──► videoCourseChapter... ──► videoCourseContent...
   ──► submitVideoCourseContentLog / submitVideoCourseQuizLog ──► rateVideoCourse
```

| Tujuan | Operasi | Payload kunci |
|--------|---------|---------------|
| Katalog | `videoCourses`, `videoCourse(id)`, `countVideoCourses`, `categories` | filter `categoryId`, `search` |
| Milik saya / company | `myVideoCourses(param: InputBaseQuery)`, `companyVideoCourses`, `companyVideoCourseByCompanyId` | |
| Cek akses | `isEmployeeCanAccessVideoCourse(videoCourseId)`, `videoCourseAccess` | |
| Struktur | `videoCourseChapterByVideoCourseId`, `videoCourseContentByVideoCourseChapterId`, `videoCourseContentById` | |
| Assign (admin) | `createUserProgramByVideoCourseId(videoCourseId)`, `assignUserProgramByVideoCourseId(videoCourseId, userIds, endDate)` | |
| Tandai progress | `submitVideoCourseContentLog(videoCourseContentId)` | |
| Submit kuis/survey | `submitVideoCourseQuizLog(contentId, answers)`, `submitVideoCourseSurveyLog(contentId, answers)` | `answers: [ContentLogAnswerInput!]` |
| Beri rating | `rateVideoCourse(videoCourseId, rating, feedback)` | |
| Kelola company VC (admin) | `createCompanyVideoCourse`, `blukCreateCompanyVideoCourse(companyId, videoCourseIds)`, `deleteCompanyVideoCourse`, `updateCompanyVideoCourseAccess(videoCourseId, access)` | |

---

### 4.7 FLOW: Bootcamp (kelas terjadwal)

Bootcamp adalah modul terbesar. Hirarki:
**Bootcamp → BootcampContent (sesi) → BootcampQuiz / BootcampSubmission → Log/Grading**.

```
createBootcamp ──► createBootcampContent ──► createBootcampQuiz / createBootcampSubmission
        │                                          └──► createBootcampQuestion / ...EssayQuestion
        ├──► assignUserBootcamp (peserta)
        ├──► assignMentor (mentor)
        └──► (peserta absen, kerjakan quiz/submission) ──► grading ──► generateFinalScore ──► sertifikat
```

#### 1) Buat Bootcamp
```graphql
mutation CreateBootcamp($input: InputBootcamp!) {
  createBootcamp(input: $input) { id title startedAt finishedAt }
}
```
```json
{ "input": {
    "title": "Data Analytics Batch 5",
    "descriptions": "Bootcamp 3 bulan",
    "angkatanId": 5, "divisionId": "<uuid-division>",
    "learningOutcomes": "...", "enrollmentKey": "DA-2026",
    "syllabus": "...", "learningContract": "...",
    "startedAt": "2026-07-01", "finishedAt": "2026-09-30",
    "attendancePercentage": 20, "attendancePercentageToggle": true,
    "avgTestScorePercentage": 30, "avgTestScorePercentageToggle": true,
    "finalProjectPercentage": 50, "finalProjectPercentageToggle": true,
    "isAllFinproRequired": true, "imageMediaId": "<uuid-media>"
} }
```

#### 2) Buat Sesi / Content
```graphql
mutation CreateBootcampContent($input: InputBootcampContent!) {
  createBootcampContent(input: $input) { id title liveClassTime }
}
```
```json
{ "input": {
    "bootcampId": "<uuid-bootcamp>",
    "title": "Sesi 1 - Intro to SQL",
    "descriptions": "...",
    "liveClassTime": "2026-07-02T09:00:00.000Z",
    "liveClassDuration": 120,
    "zoomUrl": "https://zoom.us/...",
    "videoPreClassUrl": "https://...", "videoPostClassUrl": "https://...",
    "isAttendanceCounted": true,
    "enableCheckIn": true, "checkInKey": "MASUK01",
    "enableCheckOut": true, "checkOutKey": "PULANG01",
    "learningResourses": [{ "name": "Slide", "url": "https://...", "apiVideoId": null }]
} }
```

#### 3) Buat Quiz + Soal
```graphql
mutation CreateBootcampQuiz($input: InputBootcampQuiz!) {
  createBootcampQuiz(input: $input) { id title type deadline }
}
```
```json
{ "input": {
    "title": "Post Test Sesi 1", "type": "POST_TEST",
    "bootcampId": "<uuid-bootcamp>", "contentId": "<uuid-content>",
    "startDate": "2026-07-02T11:00:00.000Z",
    "deadline": "2026-07-03T23:59:59.000Z",
    "timer": 60, "needGradingConfirmation": false, "mentorId": "<uuid-mentor>"
} }
```
Tambah soal pilihan ganda:
```graphql
mutation CreateBootcampQuestion($input: InputBootcampQuestion!) {
  createBootcampQuestion(input: $input) { id question }
}
```
```json
{ "input": {
    "quizId": "<uuid-quiz>", "order": 1, "type": "MULTIPLE_CHOICE",
    "question": "SELECT * FROM ...?", "questionScore": 10,
    "answer": { "option": ["A", "B", "C", "D"], "answer": [2] }
} }
```
Soal esai: `createBootcampEssayQuestion(input: InputBootcampEssayQuestion!)`
→ `{ quizId, order, question, questionScore, defaultEvaluation }`.

#### 4) Buat Submission (project/tugas)
```graphql
mutation CreateBootcampSubmission($input: InputBootcampSubmission!) {
  createBootcampSubmission(input: $input) { id title deadline }
}
```
```json
{ "input": {
    "bootcampId": "<uuid-bootcamp>", "title": "Mini Project 1",
    "description": "...", "type": "MINI_PROJECT", "submissionType": "LINK",
    "deadline": "2026-07-15T23:59:59.000Z",
    "isGroupSubmission": false, "isNoPenalty": false,
    "attachmentUrl": ["https://.../brief.pdf"],
    "mentorId": "<uuid-mentor>",
    "gradingCriteria": [
      { "assessmentAspect": "Kelengkapan",
        "gradingDetail": [
          { "assessmentDetail": "Lengkap", "maxScore": 100 },
          { "assessmentDetail": "Sebagian", "maxScore": 60 }
        ] }
    ]
} }
```

#### 5) Assign peserta & mentor
```graphql
mutation Assign($bootcampId: String!, $userIds: [String!]!) {
  assignUserBootcamp(bootcampId: $bootcampId, userIds: $userIds)
  assignMentor(bootcampId: $bootcampId, userIds: $userIds) { id }
}
```

#### 6) Sisi Peserta — absensi & pengerjaan

| Aksi | Operasi | Payload |
|------|---------|---------|
| Absen check-in/out | `createBootcampContentLog(input: BootcampContentLogInput!)` | `{ contentId, key, action }` (action: `CHECK_IN`/`CHECK_OUT`) |
| Mulai/simpan quiz | `createQuizLog` / `submitNewQuizLog` / `updateQuizLog` / `submitQuizLog` (input `BootcampQuizLogInput` / `UpdateQuizLogInput`) | `{ quizId, timeleft, answers[], essayAnswer[], status, score }` |
| Submit project | `submitBootcampSubmission(input: CreateBootcampSubmissionLog!)` | `{ submissionUrl, submissionId, userGroupIds[] }` |
| Update submit | `updateSubmitBootcampSubmission(submissionId, submissionUrl)` | |
| Quiz/submission saya | `myBootcampQuizLog(quizId)`, `myBootcampSubmissionLog(submissionId)`, `mybootcampContentLog` | |
| Bootcamp saya | `myBootcamps(param: BootcampBaseQuery!)`, `countMyBootcamps` | |

`BootcampQuizLogInput` contoh:
```json
{ "input": {
    "quizId": "<uuid-quiz>", "status": "SUBMITTED", "timeleft": 0, "score": 0,
    "answers": [{ "questionId": "<uuid-q>", "answer": [2] }],
    "essayAnswer": [{ "questionId": "<uuid-eq>", "essay": "Jawaban..." }]
} }
```

#### 7) Sisi Mentor/Admin — grading & penilaian

| Aksi | Operasi | Payload kunci |
|------|---------|---------------|
| Nilai esai quiz | `essayGrading(id, input: BootcampEssayGradingInput!)` | `{ essayAnswer[], bonusScore, bonusReason }` |
| Nilai submission | `gradeUserSubmission(id, input: UserSubmissionUpdate!)` / `gradeUserGroupSubmission` | `{ totalScore, gradingCriteriaScore[], comment, bonusScore }` |
| Toggle selesai grading | `quizFinishGradingToggle(id)`, `submissionFinishGradingToggle(id)` | |
| Recalculate skor | `recalculateQuizLogMCScore(quizId)` | |
| Skor akhir | `generateFinalScore(bootcampBatchId)`, `averagePostTestScore`, `averageSubmissionScore` | |
| Report card & sertifikat | `updateUserBootcampReportCard(userBootcampId, reportCardUrl)`, `generateUserBootcampCertificate(userBootcampId)` | |
| Laporan | `testReport`, `projectReport`, `attendanceReport`, `bootcampQuizChart`, `bootcampSubmissionChart` | per `bootcampId` |
| Export Excel | `exportScoreQuizExcel`, `exportFinalScoreExcel`, `exportBootcampQuizLogExcel`, `exportBootcampSubmissionLogExcel`, `exportProjectReportExcel`, `exportAttendanceReportExcel`, `exportAssignedBootcampEmployee` | → URL |
| Mentor lihat tugasnya | `mentorBootcamp`, `mentorBootcampQuiz`, `mentorBootcampSubmission` (+ varian `...ByMentorId`) | |
| Kelola mentor | `assignedMentor`, `unassignedMentor`, `assignMentor`, `unassignMentor` | |

#### 8) Notifikasi & file bootcamp

| Tujuan | Operasi |
|--------|---------|
| Setting notifikasi | `getUserNotificationSettingByMyCompany`, `getUserNotificationSettingByBootcampBatchId`, `updateUserNoitificationSettingByMyCompany(input: SettingInput!)`, `updateUserNoitificationSettingByBootcampBatchId` |
| File peserta | `userBootcampFiles`, `myUserBootcampFiles`, `createUserBootcampFile`, `updateUserBootcampFile`, `deleteUserBootcampFile` |
| Google Calendar | `syncGoogleCalendar(bootcampId)`, `googleLoginURL`, `saveCompanyGoogleToken(code)`, `googleCompanyToken` |

---

### 4.8 FLOW: Pengumuman (Announcement)

```
createAnnouncement ──► (target divisi/program/semua) ──► myUserAnnouncement (sisi user) ──► setIsReadAnnouncement
```

```graphql
mutation CreateAnnouncement($input: AnnouncementInput!) {
  createAnnouncement(input: $input) { id title }
}
```
```json
{ "input": {
    "title": "Libur Nasional", "content": "<p>Kantor tutup...</p>",
    "isForAllEmployee": false,
    "division": [{ "id": "<uuid-div>", "title": "Engineering" }],
    "program": [{ "id": "<uuid-prog>", "title": "Onboarding" }]
} }
```
Lainnya: `announcements`, `announcementById`, `updateAnnouncement`, `deleteAnnouncement`,
`announcementReceivers`, `myUserAnnouncement(lastId, limit)`, `setIsReadAnnouncement(announcementIds)`,
plus varian bootcamp: `createBootcampAnnouncement`, `bootcampAnnouncements`, dst.

---

### 4.9 FLOW: Forum (diskusi)

```
forums / forumById ──► createForum (pertanyaan/balasan via parentId) ──► upForumVotes ──► toggleIsAnswerForum
```

```graphql
mutation CreateForum($input: InputForum!) { createForum(input: $input) { id text } }
```
```json
{ "input": {
    "title": "Pertanyaan tentang SQL Join",
    "type": "QUESTION", "text": "<p>Bagaimana cara...</p>",
    "bootcampContentId": "<uuid>", "parentId": null
} }
```
Lainnya: `forums(query: ForumBaseQuery!)`, `countForums`, `forumParticipant`,
`updateForum`, `deleteForum`, `upForumVotes(forumId, value)`, `toggleIsAnswerForum(forumId)`.

---

### 4.10 FLOW: Media & Penyimpanan

```
availableStorage ──► createMedia / createRawMedia ──► (upload) ──► updateRawMedia ──► medias
```

| Tujuan | Operasi | Payload kunci |
|--------|---------|---------------|
| Cek kuota | `availableStorage` | |
| Upload langsung | `uploadFile(file: Upload!, isPrivate, alloweds)`, `createMedia(file, title, isPrivate)` | multipart |
| Upload via pre-signed | `createRawMedia(title, fileName, fileSize, mimeType)` → `preUpdateRawMedia` → `updateRawMedia(id, url, externalId, response, status, title)` | |
| List / detail | `medias(types, type, status, param)`, `mediaById(id)`, `countMedias` | |
| Update / hapus | `updateMedia`, `updateMediaData(id, title)`, `deleteMedia(id)` | |
| api.video | `apiVideoList(param: VideosApiListParam!)`, `generateAPIVideoToken(videoId)`, `generateAPIVideoAttributeFromUrl(url)`, `createMediaFromApiVideoId(videoId)` | |

---

### 4.11 FLOW: Company Profile & Sertifikat (admin)

| Tujuan | Operasi | Payload |
|--------|---------|---------|
| Profil company saya | `myCompany`, `companyById(id)`, `companyBySlug(slug)` | |
| Update profil | `updateMyCompanyProfile(input: MyCompanyProfileInput!)` | termasuk `certificateProperty`, `smtpData`, `themeId` |
| Tema | `themes(search)`, `themeById(id)` | |
| Preview sertifikat | `generatePreviewCertificate(input: LMSB2BCertificateParam!)`, `generateFinalScore` | |

```json
// MyCompanyProfileInput (sebagian)
{ "input": {
    "name": "PT Maju Jaya", "logoUrl": "https://...", "themeId": "<uuid>",
    "vision": "...", "mission": "...", "prefix": "MJ",
    "certificateProperty": {
      "signatureName": "Direktur", "signatureRole": "CEO",
      "signatureUrl": "https://...", "certificateTemplateUrl": "https://..." },
    "smtpData": { "host": "smtp.gmail.com", "port": 587,
      "user": "mail@majujaya.com", "pass": "****",
      "name": "Maju Jaya LMS", "toggleUseCustomSetting": true }
} }
```

---

### 4.12 FLOW: Analytics & Activity Log

| Tujuan | Operasi |
|--------|---------|
| Karyawan aktif bulanan | `countMonthlyActiveEmployees`, `countMonthlyActiveEmployeeSummary`, `countMonthlyActiveEmployeeSummaryForAdmin` |
| Watch time | `countMonthlyTotalVideoCourseWatchTime`, `totalWatchTimeSummary` |
| Hitungan umum | `countAllEmployees`, `countUser`, `countDivisions`, `countCompanies`, `countPrograms` |
| Activity log | `userActivityLog`, `userActivityLogByCompanyId`, `countUserActivityLog`, `countUserActivityLogByCompanyId` |
| Export log | `exportUserActivityLog`, `exportSuperUserActivityLog` |

Parameter umum log memakai `InputBaseQuery { search, limit, page, orderColumn, orderBy }`
plus filter `events`, `entities`, `exEndpoints`, `roles`, `companyIds`, `employeeIds`.

---

## 5. Pola Umum (Pagination, Sorting, Filtering)

Mayoritas query list menerima argumen berulang:

| Argumen | Tipe | Fungsi |
|---------|------|--------|
| `page` | `Float` | nomor halaman |
| `limit` | `Float` | jumlah per halaman |
| `orderColumn` | `String` | kolom urutan, mis. `"createdAt"`, `"name"` |
| `orderBy` | `String` | `"ASC"` / `"DESC"` |
| `search` | `String` | kata kunci |
| `status` / `divisionIds` / `companyIds` | `[String!]` | filter |

Pola umum: setiap `xxx(...)` list punya pasangan `countXxx(...)` untuk total data (pagination).

Beberapa modul memakai objek param tunggal alih-alih argumen lepas, mis.
`InputBaseQuery`, `BootcampBaseQuery`, `ForumBaseQuery`, `UserBaseQuery`,
`UserBootcampFileBaseQuery`, `VideosApiListParam`.

---

## 6. Referensi Tipe Input Penting

```graphql
input EmployeeInput {
  name: String  employeeId: String  gender: String  dateOfBirth: DateTime
  email: String  phoneNumber: String  address: String  divisionId: String
  employeeRole: String  angkatanId: Float  nik: String  npwp: String
  username: String  profilePictureUrl: String
}

input ProgramInput { title: String!  description: String  type: String!  isSequential: Boolean }

input ChapterInput { title: String!  description: String  order: Float  programId: String! }

input ContentInput {
  title: String!  description: String  order: Float  chapterId: String!  type: String!
  thumbnailUrl: String  duration: Float  mediaType: String  articleType: String
  article: String  videoUrl: String  mediaId: String  attachmentUrl: [String!]
  contentAttachment: [String!]  isRandomQuestion: Boolean  randomQuestionCount: Float
}

input QuestionInput { order: Float  type: String  question: String
  options: [String!]  answers: [Float!]  contentId: String }

input AssignProgramInput { programId: String!  employeeIds: [String!]!
  startDate: DateTime  endDate: DateTime! }

input ContentLogAnswerInput { questionId: String!  answers: [Float!]  essayAnswer: String }

input AnnouncementInput { title: String!  content: String!  isForAllEmployee: Boolean
  division: [AnnouncementReceiverDataInput!]  program: [AnnouncementReceiverDataInput!] }

input InputBootcamp { title  descriptions  angkatanId  divisionId  learningOutcomes
  enrollmentKey  syllabus  learningContract  startedAt  finishedAt
  attendancePercentage(+Toggle)  avgTestScorePercentage(+Toggle)
  avgProjectScorePercentage(+Toggle)  midTestPercentage(+Toggle)
  finalTestPercentage(+Toggle)  miniProjectPercentage(+Toggle)
  finalProjectPercentage(+Toggle)  technicalCaseStudyPercentage(+Toggle)
  isAllFinproRequired  feedbackFormId  imageMediaId }

input BootcampQuizLogInput { quizId: String!  timeleft: Int  status: String!  score: Int
  answers: [AnswerLogInput!]  essayAnswer: [EssayAnswerLogInput!] }

input UserSubmissionUpdate { submissionUrl  submissionId  totalScore
  gradingCriteriaScore: [GradingCriteriaScoreInput!]  comment  bonusScore  bonusReason }
```

> Catatan: semua field bertipe `String` polos (enum tidak dipakai di schema). Nilai seperti
> `type`, `status`, `role`, `action`, `submissionType` divalidasi di sisi server, bukan oleh GraphQL.
> Nilai pasti (mis. `"ONBOARDING"`, `"POST_TEST"`, `"CHECK_IN"`) sebaiknya dikonfirmasi ke tim backend.

---

## 6b. Contoh Response Nyata (Terverifikasi 2026-06-28)

Bagian ini berisi hasil **eksekusi langsung** ke endpoint produksi memakai akun admin
(`arwendymelyn@dibimbing.id`, company **Bootcamp QA** / `dibimbingqa`,
companyId `811637b1-9989-4d45-a9f5-220c5f2354f7`).

#### Login berhasil
Request `login(companyId, usernameOrEmail, password)` →
```json
{ "data": { "login": { "errors": null, "user": {
  "id": "8de8e4e8-a686-4b94-8683-580dc84fbfb1",
  "name": "Wendi", "email": "arwendymelyn@dibimbing.id",
  "role": "admin", "status": "active",
  "companyId": "811637b1-9989-4d45-a9f5-220c5f2354f7",
  "company": { "id": "811637b1-9989-4d45-a9f5-220c5f2354f7", "name": "Bootcamp QA", "slug": "dibimbingqa" }
} } } }
```

#### myCompany
```json
{ "data": { "myCompany": {
  "id": "811637b1-9989-4d45-a9f5-220c5f2354f7", "name": "Bootcamp QA", "slug": "dibimbingqa",
  "countEmployee": 2367, "countActiveEmployee": 0, "countClass": 3, "countVideoCourse": 0
} } }
```

#### employees (limit 3, total 2367)
```json
{ "data": { "employees": [
  { "id": "96fa2056-3544-47e5-891b-a112c985ebe4", "name": "Arief Rahman Hakim", "email": "ariefrahman14@gmail.com", "status": "active" },
  { "id": "3e3a0c6e-31b5-45a5-a8af-ec7421ded030", "name": "a", "email": "reza27fahmi@gmail.com", "status": "active" }
], "countEmployees": 2367 } }
```

#### bootcamps (total 3)
```json
{ "data": { "bootcamps": [
  { "id": "89d0007c-f0cd-4071-83f5-3542bb989a85", "title": "Weekly Class UIUX", "startedAt": "1765126800000", "finishedAt": "1766941200000", "countAssignedUser": 0 },
  { "id": "5be8d98c-d721-4d9b-9962-dd546afada5d", "title": "Kelas Test", "startedAt": "1764522000000", "finishedAt": "1766941200000", "countAssignedUser": 2 }
], "countBootcamps": 3 } }
```

#### programs (total 2393) & announcements (total 710)
```json
{ "data": { "programs": [
  { "id": "24a553c9-f4ea-44f4-bc86-2f317091b198", "title": "test", "type": "training", "countChapters": 0, "countContents": 1, "countAssignedEmployee": 0 }
], "countPrograms": 2393 } }
```

### ⚠️ Catatan PENTING dari data nyata

1. **Tanggal = epoch milidetik dalam bentuk String.** Field `createdAt`, `startedAt`,
   `finishedAt`, `liveClassTime`, dll. dikembalikan seperti `"1770140217425"` (bukan ISO).
   Konversi di klien: `new Date(Number(value))`.
2. **Nilai enum memakai huruf kecil.** Hasil nyata: `role: "admin"`, `status: "active"`,
   program `type: "training"`. Jadi untuk filter gunakan nilai seperti `"active"`, `"training"`,
   bukan `"ACTIVE"`/`"ONBOARDING"`. (Contoh `"ONBOARDING"` di atas hanya placeholder —
   konfirmasi nilai pasti per modul.)
3. **Beberapa `countXxx` bootcamp WAJIB diberi `param`.** `countBootcamps`, `countBootcampContents`,
   dst. error `Cannot read properties of undefined (reading 'search')` bila `param` tidak dikirim.
   Kirim minimal `{ "param": { "search": "" } }` walau schema menandainya opsional.
4. **`login` butuh `companyId` yang TEPAT.** companyId harus milik perusahaan tempat user
   terdaftar. Slug perusahaan bisa diresolusi via `companyBySlug(slug)` (query publik) →
   ambil `id`-nya untuk dipakai di `login`.
5. **`me` mengembalikan `company: null`** pada beberapa pemanggilan (gunakan `myCompany` untuk
   detail perusahaan yang lengkap).

---

## 7. Ringkasan

- **Endpoint tunggal**: `POST https://lmsb2b.do.dibimbing.id/graphql`
- **2 lapis auth**: HTTP Basic (`b2bserveruser`) untuk akses server + session cookie hasil `login`/`loginSuperAdmin` untuk identitas user.
- **Proteksi**: `isAuth` (login) dan `isSuperAdmin` (super admin) per resolver.
- **159 query + 159 mutation**, dikelompokkan dalam flow: Auth → Company → Employee/Division → Program → Learner → Video Course → Bootcamp → Announcement → Forum → Media → Analytics.
- **Pola**: pasangan `xxx` + `countXxx`, pagination via `page/limit/orderColumn/orderBy/search`.

> ⚠️ **Keamanan**: kredensial pada dokumen ini berasal dari `.env`. Jangan commit `.env`
> ke repository publik dan rotasi kredensial bila sudah pernah terekspos.
