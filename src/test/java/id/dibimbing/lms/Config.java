package id.dibimbing.lms;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Membaca konfigurasi dari file .env (format "KEY: value" atau "KEY=value").
 *
 * Urutan pencarian file .env:
 *   1. System property -Denv.file=/path/ke/.env
 *   2. .env     (di dalam folder module restassured-tests -> lokasi default)
 *   3. ../.env  (fallback ke root repo)
 *
 * Setiap nilai juga bisa di-override lewat System property atau environment variable
 * dengan nama key yang sama (mis. -DCOMPANY_ID=... atau export PASSWORD_ADMIN=...).
 */
public final class Config {

    private static final Map<String, String> VALUES = new HashMap<>();

    static {
        load();
    }

    private Config() {
    }

    private static void load() {
        Path envPath = resolveEnvPath();
        if (envPath != null && Files.exists(envPath)) {
            try (BufferedReader br = new BufferedReader(new FileReader(envPath.toFile()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int sep = indexOfSeparator(trimmed);
                    if (sep < 0) {
                        continue;
                    }
                    String key = trimmed.substring(0, sep).trim();
                    String value = trimmed.substring(sep + 1).trim();
                    // buang komentar inline sederhana
                    VALUES.put(key, value);
                }
                System.out.println("[Config] Loaded .env from: " + envPath.toAbsolutePath());
            } catch (Exception e) {
                System.out.println("[Config] Gagal membaca .env (" + envPath + "): " + e.getMessage());
            }
        } else {
            System.out.println("[Config] File .env tidak ditemukan, hanya pakai System property / env var.");
        }
    }

    private static int indexOfSeparator(String line) {
        int colon = line.indexOf(':');
        int eq = line.indexOf('=');
        if (colon < 0) return eq;
        if (eq < 0) return colon;
        return Math.min(colon, eq);
    }

    private static Path resolveEnvPath() {
        String custom = System.getProperty("env.file");
        if (custom != null && !custom.isBlank()) {
            return Paths.get(custom);
        }
        Path local = Paths.get(".env");
        if (Files.exists(local)) {
            return local;
        }
        return Paths.get("..", ".env");
    }

    /** Ambil nilai: prioritas System property > env var > .env > default. */
    public static String get(String key, String defaultValue) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) return sys;
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) return env;
        return VALUES.getOrDefault(key, defaultValue);
    }

    // ---- Akses bernama ----
    public static String baseUrl() {
        return get("BASE_URL", "https://lmsb2b.do.dibimbing.id/graphql");
    }

    public static String basicUser() {
        return get("USERNAME", "b2bserveruser");
    }

    public static String basicPass() {
        return get("PASSWORD", "");
    }

    public static String adminEmail() {
        return get("ADMIN", "");
    }

    public static String adminPassword() {
        return get("PASSWORD_ADMIN", "");
    }

    public static String companyId() {
        return get("COMPANY_ID", "");
    }

    public static String companySlug() {
        return get("COMPANY_SLUG", "");
    }
}
