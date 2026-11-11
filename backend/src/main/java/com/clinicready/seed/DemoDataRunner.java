package com.clinicready.seed;

import java.time.LocalDate;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Seeds the demo clinic on every start when the {@code demo} profile is active. */
@Component
@Profile("demo")
class DemoDataRunner implements ApplicationRunner {

    private final DemoDataSeeder seeder;

    DemoDataRunner(DemoDataSeeder seeder) {
        this.seeder = seeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seeder.seed(LocalDate.now(DemoDataGenerator.CLINIC_ZONE));
    }
}
