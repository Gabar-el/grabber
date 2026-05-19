package it.dsms.grabber;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point Spring Boot per la web application grabber.
 *
 * <p>Avvio: {@code mvn spring-boot:run}
 * <p>Health check: {@code GET http://localhost:8080/actuator/health}
 * <p>Swagger UI: {@code http://localhost:8080/swagger-ui.html}
 *
 * <p>La CLI legacy resta funzionante tramite {@link Main} durante la transizione
 * (Phase 1–4). Sarà rimossa in Phase 5 dopo che tutti i comandi CLI
 * saranno esposti come endpoint REST.
 *
 * @see Main
 */
@SpringBootApplication
public class GrabberApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrabberApplication.class, args);
    }
}
