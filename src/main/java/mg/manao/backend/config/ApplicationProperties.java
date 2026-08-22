package mg.manao.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriétés personnalisées de l'application MANAO.
 * Mappage des propriétés du fichier application.properties avec le préfixe "app".
 */
@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class ApplicationProperties {

    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private String frontendUrl;
    private Seed seed = new Seed();
    private Evolution evolution = new Evolution();
    private Email email = new Email();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long expirationMs;
    }

    @Getter
    @Setter
    public static class Cors {
        private String allowedOrigins;
    }

    @Getter
    @Setter
    public static class Seed {
        private boolean enabled;
        private String adminEmail;
        private String adminPassword;
    }

    @Getter
    @Setter
    public static class Evolution {
        private boolean enabled;
        private String apiUrl;
        private String apiKey;
        private String instance;
        private String defaultCountryCode;
    }

    @Getter
    @Setter
    public static class Email {
        private boolean enabled;
        private String from;
    }
}
