package com.clinicready.seed;

import java.time.LocalDate;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.clinicready.document.DemoDocumentSeeder;

/** Seeds the demo clinic on every start when the {@code demo} profile is active. */
@Component
@Profile("demo")
class DemoDataRunner implements ApplicationRunner {

    private final DemoDataSeeder seeder;
    private final DemoDocumentSeeder documents;

    DemoDataRunner(DemoDataSeeder seeder, DemoDocumentSeeder documents) {
        this.seeder = seeder;
        this.documents = documents;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now(DemoDataGenerator.CLINIC_ZONE);
        seeder.seed(today);
        // Deterministic for the same day, so this is the same data the seeder just wrote.
        documents.seed(DemoDataGenerator.generate(today));
    }
}
