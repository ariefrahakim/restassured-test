# 🚀 Quick Start — LMS B2B diBimbing GraphQL API

Panduan 1 halaman untuk tim. Detail lengkap: [GRAPHQL_API_DOCUMENTATION.md](GRAPHQL_API_DOCUMENTATION.md).

## 1. Yang perlu diketahui
| | |
|---|---|
| **Endpoint** | `POST https://lmsb2b.do.dibimbing.id/graphql` |
| **Auth lapis 1** | HTTP Basic — `b2bserveruser` (akses server) |
| **Auth lapis 2** | mutation `login` → session cookie `sid_b2b` (identitas user) |
| **Kredensial** | ada di `restassured-tests/.env` (jangan commit ke repo publik) |

## 2. Login (wajib dulu sebelum query lain)
`login` butuh **3 hal**: `companyId`, email/username, password.
> Belum tahu `companyId`? Resolusi dari slug: `companyBySlug(slug)` → ambil `id`.

```graphql
mutation {
  login(
    companyId: "811637b1-9989-4d45-a9f5-220c5f2354f7",
    usernameOrEmail: "arwendymelyn@dibimbing.id",
    password: "****"
  ) { errors { message } user { id name role companyId } }
}
```
Sukses → server kirim cookie `sid_b2b`. **Kirim ulang cookie ini di semua request berikutnya.**

## 3. Tes pertama (curl)
```bash
EP=https://lmsb2b.do.dibimbing.id/graphql
B=b2bserveruser:<PASSWORD>
# 1) cek hidup
curl -s -u "$B" -H "Content-Type: application/json" $EP -d '{"query":"{ ping }"}'
# 2) login + simpan cookie
curl -s -u "$B" -c ck.txt -H "Content-Type: application/json" $EP \
  -d '{"query":"mutation($c:String!,$u:String!,$p:String!){login(companyId:$c,usernameOrEmail:$u,password:$p){user{id}}}","variables":{"c":"<COMPANY_ID>","u":"<ADMIN>","p":"<PASSWORD_ADMIN>"}}'
# 3) pakai cookie utk query terproteksi
curl -s -u "$B" -b ck.txt -H "Content-Type: application/json" $EP -d '{"query":"{ myCompany { name countEmployee } }"}'
```

## 4. Pakai Postman (paling gampang)
1. Import `LMS-B2B-diBimbing.postman_collection.json`.
2. Tab **Variables** koleksi: isi `companyId`, `usernameOrEmail`, `password` (kolom **Current Value**).
3. Jalankan **`1. Auth > login`** → cookie tersimpan otomatis.
4. Jalankan request lain (13 folder: Health, Auth, Companies, Employees, Program, Learner, Video Course, Bootcamp, Announcement, Forum, Media, Mentor/Grading, Activity Log).

## 5. Pakai test otomatis (RestAssured + TestNG)
```bash
cd restassured-tests
mvn test            # baca restassured-tests/.env otomatis
```

## 6. ⚠️ 5 jebakan yang sering bikin error
1. **Lupa `companyId` yang benar** → `wrong username or password`. companyId harus milik perusahaan user.
2. **Lupa kirim cookie `sid_b2b`** → `not authenticated`.
3. **Tanggal = epoch milidetik String** (`"1770140217425"`), bukan ISO. Konversi: `new Date(Number(x))`.
4. **Enum huruf kecil**: `active`, `admin`, `training` (bukan `ACTIVE`).
5. **`countBootcamps` & count bootcamp lain WAJIB `param`** → kirim `{param:{search:""}}` walau terlihat opsional.

## 7. Pola umum query
- List selalu berpasangan: `xxx(...)` + `countXxx(...)` untuk pagination.
- Argumen umum: `page`, `limit`, `orderColumn`, `orderBy` (`ASC`/`DESC`), `search`.
- Operasi terproteksi: `isAuth` (login) atau `isSuperAdmin` (super admin).
