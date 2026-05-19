package it.dsms.grabber.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configurazione Spring per i bean di supporto del progetto grabber.
 */
@Configuration
public class GrabberConfig {

    /**
     * ObjectMapper condiviso tra i servizi.
     * - JavaTimeModule: serializza OffsetDateTime, Instant ecc.
     * - WRITE_DATES_AS_TIMESTAMPS=false: ISO-8601 string, non epoch
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
