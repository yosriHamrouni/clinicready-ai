package com.clinicready.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** The dev seed hashes must be BCrypt and match the documented password "password". */
class DevSeedPasswordTest {

    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

    @Test
    void devSeedHashesMatchDocumentedPassword() throws IOException {
        String sql = new ClassPathResource("db/seed-dev/R__dev_users.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        List<String> hashes = BCRYPT.matcher(sql).results().map(m -> m.group()).toList();

        assertThat(hashes).hasSize(3);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertThat(hashes).allSatisfy(hash -> assertThat(encoder.matches("password", hash)).isTrue());
    }
}
