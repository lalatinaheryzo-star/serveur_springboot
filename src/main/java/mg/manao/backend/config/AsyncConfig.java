package mg.manao.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Pool dédié aux tâches asynchrones "best effort" (email, WhatsApp).
 *
 * Avant ce changement, l'envoi de l'e-mail de confirmation
 * (ReservationService.updateStatut) se faisait de façon SYNCHRONE, sur le
 * thread de la requête HTTP : le Président attendait la fin du handshake SMTP
 * (souvent 1 à 3 s avec Gmail) avant de recevoir la réponse "réservation
 * validée". Avec @Async, la validation répond immédiatement et l'e-mail part
 * en arrière-plan — comportement et contenu de l'e-mail strictement
 * identiques, seule la latence perçue par l'utilisateur change.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("notif-async-");
        executor.initialize();
        return executor;
    }
}
