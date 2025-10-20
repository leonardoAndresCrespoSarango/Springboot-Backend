// com.etikos.user.config.FirebaseConfig
package com.etikos.user.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;

/**
 * Firebase configuration removed - Now using local JWT authentication
 * This file is kept for reference but is no longer active
 */
@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials}")
    private String credentialsPath;

    @PostConstruct
    public void initialize() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            InputStream serviceAccount = getClass().getClassLoader()
                    .getResourceAsStream("firebase-service-account.json");

            if (serviceAccount == null) {
                throw new IOException("Firebase service account file not found");
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);
        }
    }
}
