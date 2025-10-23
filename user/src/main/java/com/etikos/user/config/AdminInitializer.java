package com.etikos.user.config;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final PasswordEncoder passwordEncoder;

    @Value("${admin.default.username}")
    private String adminUsername;

    @Value("${admin.default.email}")
    private String adminEmail;

    @Value("${admin.default.password}")
    private String adminPassword;

    @Value("${admin.default.name}")
    private String adminName;

    @Value("${admin.default.lastname}")
    private String adminLastname;

    public AdminInitializer(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Checking if default admin user exists...");

        Firestore db = FirestoreClient.getFirestore();

        // Verificar si ya existe un usuario con rol ADMIN
        List<QueryDocumentSnapshot> adminUsers = db.collection("users")
                .whereEqualTo("role", "ADMIN")
                .get().get().getDocuments();

        if (!adminUsers.isEmpty()) {
            log.info("Admin user already exists. Skipping creation.");
            return;
        }

        // Verificar si el email o username ya existen
        List<QueryDocumentSnapshot> existingEmail = db.collection("users")
                .whereEqualTo("email", adminEmail)
                .get().get().getDocuments();

        List<QueryDocumentSnapshot> existingUsername = db.collection("users")
                .whereEqualTo("username", adminUsername)
                .get().get().getDocuments();

        if (!existingEmail.isEmpty() || !existingUsername.isEmpty()) {
            log.warn("Admin email or username already exists but with different role. Skipping creation.");
            return;
        }

        // Crear usuario administrador por defecto
        String uid = db.collection("users").document().getId();

        Map<String, Object> adminData = new HashMap<>();
        adminData.put("uid", uid);
        adminData.put("username", adminUsername);
        adminData.put("email", adminEmail);
        adminData.put("password", passwordEncoder.encode(adminPassword));
        adminData.put("name", adminName);
        adminData.put("lastname", adminLastname);
        adminData.put("role", "ADMIN");
        adminData.put("disabled", false);
        adminData.put("createdAt", com.google.cloud.Timestamp.now());
        adminData.put("updatedAt", com.google.cloud.Timestamp.now());
        adminData.put("biometricEnabled", false);
        adminData.put("totpEnabled", false);
        adminData.put("totpSecret", null);

        db.collection("users").document(uid).set(adminData).get();

        log.info("=============================================================");
        log.info("DEFAULT ADMIN USER CREATED SUCCESSFULLY");
        log.info("Email: {}", adminEmail);
        log.info("Username: {}", adminUsername);
        log.info("Password: {}", adminPassword);
        log.info("IMPORTANT: Please change the default password immediately!");
        log.info("=============================================================");
    }
}

