package by.andd3dfx;

import by.andd3dfx.service.BitbucketService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Credentials, repository and export path are loaded from .env:
 * EMAIL - Atlassian account email
 * TOKEN - Bitbucket API token
 * REPOSITORY_URL - Bitbucket API repository URL
 * CSV_PATH_TO_EXPORT - path to export CSV file
 */
public class Application {

    public static void main(String[] args) throws IOException {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.getEnvironment().getPropertySources().addFirst(loadDotEnv(Path.of(".env")));
        ctx.scan("by.andd3dfx");
        ctx.refresh();

        BitbucketService exportService = ctx.getBean(BitbucketService.class);
        System.out.println("Starting export...");
        try {
            exportService.export();
            System.out.println("Export finished.");
        } catch (HttpClientErrorException.Unauthorized e) {
            String email = ctx.getEnvironment().getProperty("EMAIL");
            System.err.println("Bitbucket returned 401 Unauthorized for EMAIL=" + email + " from .env");
            throw e;
        } finally {
            ctx.close();
        }
    }

    private static MapPropertySource loadDotEnv(Path envFile) throws IOException {
        if (!Files.exists(envFile)) {
            throw new IllegalStateException(".env not found in " + envFile.toAbsolutePath().getParent());
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String line : Files.readAllLines(envFile)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = trimmed.substring(0, separator).trim();
            String value = unquote(trimmed.substring(separator + 1).trim());
            properties.put(key, value);
        }
        if (isBlank(properties.get("EMAIL")) || isBlank(properties.get("TOKEN"))
                || isBlank(properties.get("REPOSITORY_URL"))) {
            throw new IllegalStateException(".env must contain EMAIL, TOKEN and REPOSITORY_URL");
        }
        System.out.println("Loaded credentials from .env, EMAIL=" + properties.get("EMAIL"));
        return new MapPropertySource("dotenv", properties);
    }

    private static boolean isBlank(Object value) {
        return value == null || value.toString().isBlank();
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
