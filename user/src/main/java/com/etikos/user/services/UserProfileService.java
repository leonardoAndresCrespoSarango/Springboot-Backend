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
    private final TotpService totpService;

    public UserProfileService(PasswordEncoder passwordEncoder, JwtService jwtService, TotpService totpService) {
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.totpService = totpService;
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
        userData.put("totpEnabled", false); // Campo para TOTP (Google Authenticator)
        userData.put("totpSecret", null); // Secreto TOTP (se genera cuando se habilita)

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

        // Verificar si el usuario tiene TOTP habilitado
        Boolean totpEnabled = userDoc.getBoolean("totpEnabled");
        if (totpEnabled != null && totpEnabled) {
            // Si tiene TOTP habilitado, no generar token aún, requerir código TOTP
            log.info("TOTP required for user: {}", req.getEmail());

            String uid = userDoc.getString("uid");
            LoginResponse response = new LoginResponse();
            response.setTotpRequired(true);
            response.setTempSessionId(uid); // Usar el UID como identificador temporal
            response.setUser(documentToDto(userDoc));
            // No se envía token aún

            return response;
        }

        // Si no tiene TOTP, generar token JWT normalmente
        String uid = userDoc.getString("uid");
        String username = userDoc.getString("username");
        String role = userDoc.getString("role");

        String token = jwtService.generateToken(username, role, uid);

        log.info("Login successful for user: {} ({})", req.getEmail(), uid);

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUser(documentToDto(userDoc));
        response.setTotpRequired(false);

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
        Boolean disabled = doc.getBoolean("disabled");
        dto.setDisabled(disabled != null && disabled);
        Boolean biometricEnabled = doc.getBoolean("biometricEnabled");
        dto.setBiometricEnabled(biometricEnabled != null && biometricEnabled);
        Boolean totpEnabled = doc.getBoolean("totpEnabled");
        dto.setTotpEnabled(totpEnabled != null && totpEnabled);
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
        Boolean disabled = (Boolean) data.get("disabled");
        dto.setDisabled(disabled != null && disabled);
        Boolean biometricEnabled = (Boolean) data.get("biometricEnabled");
        dto.setBiometricEnabled(biometricEnabled != null && biometricEnabled);
        Boolean totpEnabled = (Boolean) data.get("totpEnabled");
        dto.setTotpEnabled(totpEnabled != null && totpEnabled);
        return dto;
    }

    // ==================== MÉTODOS TOTP ====================

    /**
     * Inicia la configuración de TOTP para un usuario.
     * Genera un secreto y devuelve el QR code para escanear con Google Authenticator
     */
    public Map<String, String> setupTotp(String uid) throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        DocumentSnapshot doc = db.collection("users").document(uid).get().get();

        if (!doc.exists()) {
            throw new RuntimeException("User not found");
        }

        String email = doc.getString("email");

        // Generar un nuevo secreto TOTP
        String secret = totpService.generateSecret();

        // Generar QR code
        String qrCodeDataUri = totpService.generateQrCodeDataUri(secret, email);

        // Guardar el secreto temporalmente (aún no habilitado)
        Map<String, Object> updates = new HashMap<>();
        updates.put("totpSecret", secret);
        updates.put("updatedAt", com.google.cloud.Timestamp.now());
        db.collection("users").document(uid).update(updates).get();

        log.info("TOTP setup initiated for user: {}", uid);

        Map<String, String> result = new HashMap<>();
        result.put("secret", secret);
        result.put("qrCodeDataUri", qrCodeDataUri);

        return result;
    }

    /**
     * Verifica el código TOTP y habilita TOTP para el usuario si es correcto
     */
    public boolean verifyAndEnableTotp(String uid, String code) throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        DocumentSnapshot doc = db.collection("users").document(uid).get().get();

        if (!doc.exists()) {
            throw new RuntimeException("User not found");
        }

        String secret = doc.getString("totpSecret");
        if (secret == null || secret.isEmpty()) {
            throw new RuntimeException("TOTP not set up. Please call setup first.");
        }

        // Verificar el código TOTP
        boolean isValid = totpService.verifyCodeWithWindow(secret, code);

        if (isValid) {
            // Habilitar TOTP
            Map<String, Object> updates = new HashMap<>();
            updates.put("totpEnabled", true);
            updates.put("updatedAt", com.google.cloud.Timestamp.now());
            db.collection("users").document(uid).update(updates).get();

            log.info("TOTP enabled successfully for user: {}", uid);
            return true;
        } else {
            log.warn("Invalid TOTP code for user: {}", uid);
            return false;
        }
    }

    /**
     * Deshabilita TOTP para un usuario (requiere verificación del código actual)
     */
    public boolean disableTotp(String uid, String code) throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        DocumentSnapshot doc = db.collection("users").document(uid).get().get();

        if (!doc.exists()) {
            throw new RuntimeException("User not found");
        }

        Boolean totpEnabled = doc.getBoolean("totpEnabled");
        if (totpEnabled == null || !totpEnabled) {
            throw new RuntimeException("TOTP is not enabled for this user");
        }

        String secret = doc.getString("totpSecret");

        // Verificar el código antes de deshabilitar
        boolean isValid = totpService.verifyCodeWithWindow(secret, code);

        if (isValid) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("totpEnabled", false);
            updates.put("totpSecret", null); // Eliminar el secreto por seguridad
            updates.put("updatedAt", com.google.cloud.Timestamp.now());
            db.collection("users").document(uid).update(updates).get();

            log.info("TOTP disabled successfully for user: {}", uid);
            return true;
        } else {
            log.warn("Invalid TOTP code when trying to disable for user: {}", uid);
            return false;
        }
    }

    /**
     * Login con verificación TOTP
     * Este método se llama después del login normal cuando el usuario tiene TOTP habilitado
     */
    public LoginResponse loginWithTotp(String uid, String totpCode) throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        DocumentSnapshot userDoc = db.collection("users").document(uid).get().get();

        if (!userDoc.exists()) {
            throw new LoginFailedException("User not found", "USER_NOT_FOUND");
        }

        Boolean totpEnabled = userDoc.getBoolean("totpEnabled");
        if (totpEnabled == null || !totpEnabled) {
            throw new RuntimeException("TOTP is not enabled for this user");
        }

        String secret = userDoc.getString("totpSecret");
        if (secret == null || secret.isEmpty()) {
            throw new RuntimeException("TOTP secret not found");
        }

        // Verificar el código TOTP
        boolean isValid = totpService.verifyCodeWithWindow(secret, totpCode);

        if (!isValid) {
            log.warn("Invalid TOTP code for user: {}", uid);
            throw new LoginFailedException("Invalid TOTP code", "INVALID_TOTP");
        }

        // Código TOTP válido, generar token JWT
        String username = userDoc.getString("username");
        String role = userDoc.getString("role");
        String token = jwtService.generateToken(username, role, uid);

        log.info("Login with TOTP successful for user: {}", uid);

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUser(documentToDto(userDoc));
        response.setTotpRequired(false);

        return response;
    }

    /**
     * Obtiene el estado de TOTP para un usuario
     */
    public boolean getTotpEnabled(String uid) throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        DocumentSnapshot doc = db.collection("users").document(uid).get().get();

        if (!doc.exists()) {
            throw new RuntimeException("User not found");
        }

        Boolean enabled = doc.getBoolean("totpEnabled");
        return enabled != null && enabled;
    }

    /**
     * Login con biometría - También verifica si requiere TOTP
     * El frontend ya validó la identidad biométrica, aquí solo generamos el token
     * o solicitamos TOTP si está habilitado
     */
    public LoginResponse loginWithBiometric(String uid) throws Exception {
        log.info("Biometric login attempt for user: {}", uid);

        Firestore db = FirestoreClient.getFirestore();
        DocumentSnapshot userDoc = db.collection("users").document(uid).get().get();

        if (!userDoc.exists()) {
            log.warn("Biometric login failed: User not found: {}", uid);
            throw new LoginFailedException("User not found", "USER_NOT_FOUND");
        }

        // Verificar si está bloqueado
        Boolean disabled = userDoc.getBoolean("disabled");
        if (disabled != null && disabled) {
            log.warn("Biometric login failed: Account disabled for user: {}", uid);
            throw new LoginFailedException("User account is disabled", "ACCOUNT_DISABLED");
        }

        // Verificar si el usuario tiene biometría habilitada
        Boolean biometricEnabled = userDoc.getBoolean("biometricEnabled");
        if (biometricEnabled == null || !biometricEnabled) {
            log.warn("Biometric login failed: Biometric not enabled for user: {}", uid);
            throw new LoginFailedException("Biometric authentication not enabled", "BIOMETRIC_NOT_ENABLED");
        }

        // Verificar si el usuario tiene TOTP habilitado
        Boolean totpEnabled = userDoc.getBoolean("totpEnabled");
        if (totpEnabled != null && totpEnabled) {
            // Si tiene TOTP habilitado, no generar token aún, requerir código TOTP
            log.info("TOTP required for biometric login - user: {}", uid);

            LoginResponse response = new LoginResponse();
            response.setTotpRequired(true);
            response.setTempSessionId(uid);
            response.setUser(documentToDto(userDoc));
            // No se envía token aún

            return response;
        }

        // Si no tiene TOTP, generar token JWT normalmente
        String username = userDoc.getString("username");
        String role = userDoc.getString("role");
        String token = jwtService.generateToken(username, role, uid);

        log.info("Biometric login successful for user: {}", uid);

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUser(documentToDto(userDoc));
        response.setTotpRequired(false);

        return response;
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
