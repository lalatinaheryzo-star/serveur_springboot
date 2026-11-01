package mg.manao.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
        if (STARTED.compareAndSet(false, true)) {
            System.out.println("BackendApplication started successfully.");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (STARTED.compareAndSet(false, true)) {
            System.out.println("Backend springboot started successfully.");
        }
    }
}
