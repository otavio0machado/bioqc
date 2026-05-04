package com.bioqc.config;

import com.bioqc.entity.LabSettings;
import com.bioqc.entity.Permission;
import com.bioqc.entity.Role;
import com.bioqc.entity.User;
import com.bioqc.repository.LabSettingsRepository;
import com.bioqc.repository.UserRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bioqc.demo.seed", havingValue = "true")
public class DemoSeedRunner implements CommandLineRunner {

    private static final String DEMO_PASSWORD = "Demo123!";
    private static final String DEMO_LAB_NAME = "Lab Demo BioQC";

    private final UserRepository userRepository;
    private final LabSettingsRepository labSettingsRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers();
        seedLabSettings();
        log.info("[DEMO_SEED] Demo seed complete. Login: admin@demo.bioqc.dev / {}", DEMO_PASSWORD);
    }

    private void seedUsers() {
        if (!userRepository.existsByUsername("admin@demo.bioqc.dev")) {
            User admin = User.builder()
                .username("admin@demo.bioqc.dev")
                .email("admin@demo.bioqc.dev")
                .name("Admin Demo")
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .role(Role.ADMIN)
                .permissions(Set.of(
                    Permission.QC_WRITE,
                    Permission.REAGENT_WRITE,
                    Permission.MAINTENANCE_WRITE,
                    Permission.DOWNLOAD,
                    Permission.IMPORT))
                .isActive(true)
                .build();
            userRepository.save(admin);
            log.info("[DEMO_SEED] Created admin user");
        }

        if (!userRepository.existsByUsername("analyst@demo.bioqc.dev")) {
            User analyst = User.builder()
                .username("analyst@demo.bioqc.dev")
                .email("analyst@demo.bioqc.dev")
                .name("Analista Demo")
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .role(Role.FUNCIONARIO)
                .permissions(Set.of(
                    Permission.QC_WRITE,
                    Permission.REAGENT_WRITE,
                    Permission.DOWNLOAD))
                .isActive(true)
                .build();
            userRepository.save(analyst);
            log.info("[DEMO_SEED] Created analyst user");
        }
    }

    private void seedLabSettings() {
        if (labSettingsRepository.count() == 0) {
            LabSettings demo = LabSettings.builder()
                .labName(DEMO_LAB_NAME)
                .responsibleName("Dr. Demo Responsavel")
                .responsibleRegistration("CRBM-DEMO-00000")
                .address("Rua Ficticia, 100 - Sao Paulo, SP")
                .phone("(11) 0000-0000")
                .email("contato@demo.bioqc.dev")
                .build();
            labSettingsRepository.save(demo);
            log.info("[DEMO_SEED] Created lab settings");
        }
    }
}
