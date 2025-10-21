package com.etikos.user.services;

import com.etikos.user.dto.LoginRequest;
import com.etikos.user.dto.LoginResponse;
import com.etikos.user.dto.RegisterRequest;
import com.etikos.user.dto.UserProfileDto;
import com.etikos.user.security.JwtService;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserProfileService(PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public UserProfileDto register(RegisterRequest req) throws ExecutionException, InterruptedException {
        log.info("Attempting to register new user with email: {}", req.getEmail());

        Firestore db = FirestoreClient.getFirestore();

        // Verificar si el email ya existe
        List<QueryDocumentSnapshot> existingEmail = db.collection("users")
                .whereEqualTo("email", req.getEmail())
                .get().get().getDocuments();

        if (!existingEmail.isEmpty()) {
            log.warn("Registration failed: Email already exists: {}", req.getEmail());
            throw new RuntimeException("Email already exists");
        }

        // Verificar si el username ya existe
        List<QueryDocumentSnapshot> existingUsername = db.collection("users")
                .whereEqualTo("username", req.getUsername())
                .get().get().getDocuments();

        if (!existingUsername.isEmpty()) {
            log.warn("Registration failed: Username already exists: {}", req.getUsername());
            throw new RuntimeException("Username already exists");
        }

        // Crear nuevo documento con ID autogenerado
        String uid = db.collection("users").document().getId();

        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", uid);
        userData.put("username", req.getUsername());
        userData.put("email", req.getEmail());
        userData.put("password", passwordEncoder.encode(req.getPassword()));
        userData.put("name", req.getName());
        userData.put("lastname", req.getLastname());
        userData.put("role", "CUSTOMER");
        userData.put("disabled", false);
        userData.put("createdAt", com.google.cloud.Timestamp.now());
        userData.put("updatedAt", com.google.cloud.Timestamp.now());
        userData.put("biometricEnabled", false); // Nuevo campo para biometría

        db.collection("users").document(uid).set(userData).get();

        log.info("User registered successfully: {} ({})", req.getEmail(), uid);
        return mapToDto(userData);
    }

    public LoginResponse login(LoginRequest req) throws ExecutionException, InterruptedException {
        log.info("Login attempt for email: {}", req.getEmail());

        Firestore db = FirestoreClient.getFirestore();

        // Buscar usuario por email
        List<QueryDocumentSnapshot> docs = db.collection("users")
                .whereEqualTo("email", req.getEmail())
                .get().get().getDocuments();

        if (docs.isEmpty()) {
            log.warn("Login failed: User not found with email: {}", req.getEmail());
            throw new LoginFailedException("Invalid email or password", "USER_NOT_FOUND");
        }

        QueryDocumentSnapshot userDoc = docs.get(0);

        // Verificar si está bloqueado
        Boolean disabled = userDoc.getBoolean("disabled");
        if (disabled != null && disabled) {
            log.warn("Login failed: Account disabled for email: {}", req.getEmail());
            throw new LoginFailedException("User account is disabled", "ACCOUNT_DISABLED");
        }

        // Verificar contraseña
        String storedPassword = userDoc.getString("password");
        if (storedPassword == null || !passwordEncoder.matches(req.getPassword(), storedPassword)) {
            log.warn("Login failed: Invalid password for email: {}", req.getEmail());
            throw new LoginFailedException("Invalid email or password", "INVALID_PASSWORD");
        }

        // Generar token JWT
        String uid = userDoc.getString("uid");
        String username = userDoc.getString("username");
        String role = userDoc.getString("role");

        String token = jwtService.generateToken(username, role, uid);

        log.info("Login successful for user: {} ({})", req.getEmail(), uid);

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUser(documentToDto(userDoc));

        return response;
    }

    public List<UserProfileDto> listAll() throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        List<QueryDocumentSnapshot> docs = db.collection("users").get().get().getDocuments();

        List<UserProfileDto> users = new ArrayList<>();
        for (QueryDocumentSnapshot doc : docs) {
            users.add(documentToDto(doc));
        }
        return users;
    }

    public UserProfileDto getById(String uid) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        DocumentSnapshot doc = db.collection("users").document(uid).get().get();

        if (!doc.exists()) {
            throw new RuntimeException("User not found");
        }

        return documentToDto(doc);
    }

    public void updateCredentials(String uid, String newEmail, String newPassword) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        DocumentSnapshot doc = db.collection("users").document(uid).get().get();

        if (!doc.exists()) {
            throw new RuntimeException("User not found");
        }

        Map<String, Object> updates = new HashMap<>();

        if (newEmail != null && !newEmail.isBlank()) {
            // Verificar si el nuevo email ya existe (excepto para este usuario)
            List<QueryDocumentSnapshot> existingEmail = db.collection("users")
                    .whereEqualTo("email", newEmail)
                    .get().get().getDocuments();

            for (QueryDocumentSnapshot existing : existingEmail) {
                if (!existing.getString("uid").equals(uid)) {
                    throw new RuntimeException("Email already exists");
                }
            }
            updates.put("email", newEmail);
        }

        if (newPassword != null && !newPassword.isBlank()) {
            updates.put("password", passwordEncoder.encode(newPassword));
        }

        updates.put("updatedAt", com.google.cloud.Timestamp.now());
        db.collection("users").document(uid).update(updates).get();
    }

    public void setDisabled(String uid, boolean disabled) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();

        Map<String, Object> updates = new HashMap<>();
        updates.put("disabled", disabled);
        updates.put("updatedAt", com.google.cloud.Timestamp.now());

        db.collection("users").document(uid).update(updates).get();
    }

    public void deleteById(String uid) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        DocumentSnapshot doc = db.collection("users").document(uid).get().get();

        if (!doc.exists()) {
            throw new RuntimeException("User not found");
        }

        db.collection("users").document(uid).delete().get();
    }

    /**
     * Actualiza la preferencia de biometría del usuario
     */
    public void updateBiometricPreference(String uid, boolean enabled) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        db.collection("users").document(uid).update("biometricEnabled", enabled).get();
    }

    /**
     * Consulta si el usuario tiene biometría habilitada
     */
    public boolean getBiometricPreference(String uid) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        DocumentSnapshot doc = db.collection("users").document(uid).get().get();
        Boolean enabled = doc.getBoolean("biometricEnabled");
        return enabled != null && enabled;
    }

    /**
     * Devuelve el estado de biometría de todos los usuarios (solo para admin)
     */
    public List<Map<String, Object>> getAllUsersBiometricStatus() throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        List<QueryDocumentSnapshot> docs = db.collection("users").get().get().getDocuments();
        List<Map<String, Object>> result = new ArrayList<>();
        for (QueryDocumentSnapshot doc : docs) {
            Map<String, Object> user = new HashMap<>();
            user.put("uid", doc.getString("uid"));
            user.put("email", doc.getString("email"));
            user.put("username", doc.getString("username"));
            user.put("biometricEnabled", doc.getBoolean("biometricEnabled") != null && doc.getBoolean("biometricEnabled"));
            result.add(user);
        }
        return result;
    }

    private UserProfileDto documentToDto(DocumentSnapshot doc) {
        UserProfileDto dto = new UserProfileDto();
        dto.setUid(doc.getString("uid"));
        dto.setEmail(doc.getString("email"));
        dto.setUsername(doc.getString("username"));
        dto.setName(doc.getString("name"));
        dto.setLastname(doc.getString("lastname"));
        dto.setRole(doc.getString("role"));
        Boolean biometricEnabled = doc.getBoolean("biometricEnabled");
        dto.setBiometricEnabled(biometricEnabled != null && biometricEnabled);
        return dto;
    }

    private UserProfileDto mapToDto(Map<String, Object> data) {
        UserProfileDto dto = new UserProfileDto();
        dto.setUid((String) data.get("uid"));
        dto.setEmail((String) data.get("email"));
        dto.setUsername((String) data.get("username"));
        dto.setName((String) data.get("name"));
        dto.setLastname((String) data.get("lastname"));
        dto.setRole((String) data.get("role"));
        Boolean biometricEnabled = (Boolean) data.get("biometricEnabled");
        dto.setBiometricEnabled(biometricEnabled != null && biometricEnabled);
        return dto;
    }

    // Excepción personalizada para login fallido
    public static class LoginFailedException extends RuntimeException {
        private final String reason;

        public LoginFailedException(String message, String reason) {
            super(message);
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }
}
