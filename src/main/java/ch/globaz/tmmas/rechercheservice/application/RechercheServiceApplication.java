package ch.globaz.tmmas.rechercheservice.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée pour le démarrage de l'application
 */

@SpringBootApplication(scanBasePackages = "ch.globaz.tmmas.rechercheservice")
public class RechercheServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RechercheServiceApplication.class);
    }
}
