// com.etikos.user.controller.UserController
package com.etikos.user.controller;

import com.etikos.user.audit.AuditAction;
import com.etikos.user.audit.AuditService;
import com.etikos.user.dto.*;
import com.etikos.user.services.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@Tag(name = "Usuarios", description = "Operaciones de registro, autenticacion, MFA y administracion de usuarios")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserProfileService userService;
    private final AuditService audit;

    public UserController(UserProfileService userService, AuditService audit) {
        this.userService = userService;
        this.audit = audit;
    }

    // REGISTER (público)
    @Operation(
            summary = "Registrar un usuario",
            description = "Crea un nuevo usuario con rol CUSTOMER en Firestore y registra el evento en el servicio de auditoria."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario registrado", content = @Content(schema = @Schema(implementation = UserProfileDto.class))),
            @ApiResponse(responseCode = "400", description = "Solicitud invalida"),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @PostMapping("/register")
    public ResponseEntity<UserProfileDto> register(@Valid @RequestBody RegisterRequest req,
                                                   HttpServletRequest http) throws Exception {
        log.info("Registration request received for email: {}", req.getEmail());
        UserProfileDto created = userService.register(req);
        audit.log(created.getUid(), null, AuditAction.REGISTER, http, null);
        return ResponseEntity.ok(created);
    }

    // LOGIN (público)
    @Operation(
            summary = "Login con email y contrasena",
            description = "Valida las credenciales del usuario y devuelve un JWT. Si el usuario tiene TOTP habilitado, indica que se requiere el segundo factor."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login exitoso", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Solicitud invalida"),
            @ApiResponse(responseCode = "401", description = "Credenciales invalidas o cuenta bloqueada"),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                               HttpServletRequest http) {
        try {
            log.info("Login request received for email: {}", req.getEmail());
            LoginResponse response = userService.login(req);

            // Auditar login exitoso
            audit.log(response.getUser().getUid(), response.getUser().getUid(), AuditAction.LOGIN, http, null);

            log.info("Login successful for email: {}", req.getEmail());
            return ResponseEntity.ok(response);

        } catch (UserProfileService.LoginFailedException e) {
            // Auditar login fallido con la razón específica
            log.warn("Login failed for email: {} - Reason: {}", req.getEmail(), e.getReason());

            Map<String, Object> meta = Map.of(
                    "email", req.getEmail(),
                    "reason", e.getReason(),
                    "message", e.getMessage()
            );

            try {
                audit.log(null, null, AuditAction.LOGIN_FAILED, http, meta);
            } catch (Exception auditException) {
                log.error("Failed to log audit for failed login attempt", auditException);
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(null);

        } catch (Exception e) {
            // Error inesperado
            log.error("Unexpected error during login for email: {}", req.getEmail(), e);

            Map<String, Object> meta = Map.of(
                    "email", req.getEmail(),
                    "reason", "SYSTEM_ERROR",
                    "error", e.getClass().getSimpleName()
            );

            try {
                audit.log(null, null, AuditAction.LOGIN_FAILED, http, meta);
            } catch (Exception auditException) {
                log.error("Failed to log audit for failed login attempt", auditException);
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    // LOGIN CON BIOMETRÍA (público)
    @Operation(
            summary = "Login con biometria",
            description = "Genera un JWT para el usuario ya validado biometricamente o indica si necesita completar TOTP.",
            parameters = {
                    @Parameter(name = "uid", description = "Identificador del usuario autenticado biometricamente", required = true)
            }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login biometrico exitoso", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "401", description = "Biometria no habilitada o usuario bloqueado"),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @PostMapping("/login/biometric")
    public ResponseEntity<LoginResponse> loginWithBiometric(@RequestParam String uid,
                                                            HttpServletRequest http) {
        try {
            log.info("Biometric login request received for user: {}", uid);
            LoginResponse response = userService.loginWithBiometric(uid);

            // Si no requiere TOTP, auditar login exitoso
            if (!response.isTotpRequired()) {
                audit.log(uid, uid, AuditAction.LOGIN, http,
                        Map.of("method", "biometric"));
            }

            log.info("Biometric login successful for user: {}", uid);
            return ResponseEntity.ok(response);

        } catch (UserProfileService.LoginFailedException e) {
            log.warn("Biometric login failed for user: {} - Reason: {}", uid, e.getReason());

            Map<String, Object> meta = Map.of(
                    "uid", uid,
                    "method", "biometric",
                    "reason", e.getReason(),
                    "message", e.getMessage()
            );

            try {
                audit.log(uid, uid, AuditAction.LOGIN_FAILED, http, meta);
            } catch (Exception auditException) {
                log.error("Failed to log audit for failed biometric login", auditException);
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);

        } catch (Exception e) {
            log.error("Unexpected error during biometric login for user: {}", uid, e);

            Map<String, Object> meta = Map.of(
                    "uid", uid,
                    "method", "biometric",
                    "reason", "SYSTEM_ERROR",
                    "error", e.getClass().getSimpleName()
            );

            try {
                audit.log(uid, uid, AuditAction.LOGIN_FAILED, http, meta);
            } catch (Exception auditException) {
                log.error("Failed to log audit for failed biometric login", auditException);
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // LIST ALL USERS (solo ADMIN)
    @Operation(
            summary = "Listar usuarios",
            description = "Devuelve todos los usuarios registrados. Requiere rol ADMIN."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de usuarios", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserProfileDto.class)))),
            @ApiResponse(responseCode = "401", description = "Token invalido"),
            @ApiResponse(responseCode = "403", description = "Permisos insuficientes"),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<UserProfileDto>> listUsers(Authentication auth) throws Exception {
        log.debug("Admin listing all users");
        List<UserProfileDto> users = userService.listAll();
        return ResponseEntity.ok(users);
    }

    // GET USER BY ID (solo ADMIN)
    @Operation(
            summary = "Obtener usuario por UID",
            description = "Consulta los datos de un usuario especifico. Requiere rol ADMIN.",
            parameters = {
                    @Parameter(name = "uid", description = "Identificador del usuario en Firebase", required = true)
            }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario encontrado", content = @Content(schema = @Schema(implementation = UserProfileDto.class))),
            @ApiResponse(responseCode = "401", description = "Token invalido"),
            @ApiResponse(responseCode = "403", description = "Permisos insuficientes"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{uid}")
    public ResponseEntity<UserProfileDto> getUserById(@PathVariable String uid, Authentication auth) throws Exception {
        log.debug("Admin fetching user by ID: {}", uid);
        UserProfileDto user = userService.getById(uid);
        return ResponseEntity.ok(user);
    }

    // CREDENTIALS_UPDATED (solo ADMIN)
    @Operation(
            summary = "Actualizar credenciales del usuario",
            description = "Permite a un administrador actualizar email y/o contrasena de un usuario.",
            parameters = {
                    @Parameter(name = "uid", description = "Identificador del usuario en Firebase", required = true)
            }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credenciales actualizadas"),
            @ApiResponse(responseCode = "400", description = "Solicitud invalida"),
            @ApiResponse(responseCode = "401", description = "Token invalido"),
            @ApiResponse(responseCode = "403", description = "Permisos insuficientes"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{uid}/credentials")
    public ResponseEntity<Void> updateCredentials(@PathVariable String uid,
                                                  @RequestBody UpdateCredentialsRequest body,
                                                  HttpServletRequest http,
                                                  Authentication auth) throws Exception {
        log.info("Admin updating credentials for user: {}", uid);
        userService.updateCredentials(uid, body.getNewEmail(), body.getNewPassword());

        var meta = new java.util.HashMap<String,Object>();
        if (body.getNewEmail() != null) meta.put("newEmail", body.getNewEmail());
        audit.log(uid, principalUid(auth), AuditAction.CREDENTIALS_UPDATED, http, meta);
        return ResponseEntity.ok().build();
    }

    // BLOCK / UNBLOCK (solo ADMIN)
    @Operation(
            summary = "Bloquear o desbloquear usuario",
            description = "Actualiza el estado disabled del usuario. Requiere rol ADMIN.",
            parameters = {
                    @Parameter(name = "uid", description = "Identificador del usuario en Firebase", required = true)
            }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado del usuario actualizado"),
            @ApiResponse(responseCode = "400", description = "Solicitud invalida"),
            @ApiResponse(responseCode = "401", description = "Token invalido"),
            @ApiResponse(responseCode = "403", description = "Permisos insuficientes"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{uid}/block")
    public ResponseEntity<Void> block(@PathVariable String uid,
                                      @Valid @RequestBody BlockRequest body,
                                      HttpServletRequest http,
                                      Authentication auth) throws Exception {
        log.info("Admin {} user: {}", body.getDisabled() ? "blocking" : "unblocking", uid);
        userService.setDisabled(uid, body.getDisabled());
        audit.log(uid, principalUid(auth), body.getDisabled() ? AuditAction.USER_BLOCKED : AuditAction.USER_UNBLOCKED,
                http, null);
        return ResponseEntity.ok().build();
    }

    // DELETE USER (solo ADMIN)
    @Operation(
            summary = "Eliminar usuario",
            description = "Elimina definitivamente al usuario indicado. Requiere rol ADMIN.",
            parameters = {
                    @Parameter(name = "uid", description = "Identificador del usuario en Firebase", required = true)
            }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario eliminado"),
            @ApiResponse(responseCode = "401", description = "Token invalido"),
            @ApiResponse(responseCode = "403", description = "Permisos insuficientes"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> deleteUser(@PathVariable String uid,
                                           HttpServletRequest http,
                                           Authentication auth) throws Exception {
        log.info("Admin deleting user: {}", uid);
        userService.deleteById(uid);
        audit.log(uid, principalUid(auth), AuditAction.REGISTER, http, null);
        return ResponseEntity.ok().build();
    }

    // PASSWORD RESET (público) - Placeholder para implementación futura
    @Operation(
            summary = "Solicitar restablecimiento de contrasena",
            description = "Registra la solicitud de restablecimiento (placeholder).",
            parameters = {
                    @Parameter(name = "email", description = "Email del usuario a notificar", required = true)
            }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud registrada", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "400", description = "Solicitud invalida"),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @PostMapping("/password-reset")
    public ResponseEntity<Map<String, String>> passwordReset(@RequestParam("email") String email,
                                                             HttpServletRequest http) throws Exception {
        log.info("Password reset requested for email: {}", email);
        Map<String, Object> meta = Map.of("email", email);
        audit.log(null, null, AuditAction.PASSWORD_RESET_LINK_SENT, http, meta);
        return ResponseEntity.ok(Map.of("message", "Password reset link will be sent to email (not implemented yet)"));
    }

    // ==================== ENDPOINTS TOTP ====================

    /**
     * Inicia la configuración de TOTP para el usuario autenticado.
     * Devuelve un QR code para escanear con Google Authenticator
     */
    @Operation(
            summary = "Iniciar configuracion TOTP",
            description = "Genera un secreto y un QR para configurar TOTP. Requiere usuario autenticado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Secreto generado", content = @Content(schema = @Schema(implementation = TotpSetupResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token invalido"),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/totp/setup")
    public ResponseEntity<TotpSetupResponse> setupTotp(Authentication authentication,
                                                       HttpServletRequest http) {
        try {
            String uid = authentication.getName();
            log.info("TOTP setup requested by user: {}", uid);

            Map<String, String> setup = userService.setupTotp(uid);

            TotpSetupResponse response = new TotpSetupResponse(
                    setup.get("secret"),
                    setup.get("qrCodeDataUri")
            );

            audit.log(uid, uid, AuditAction.CREDENTIALS_UPDATED, http,
                    Map.of("action", "totp_setup_initiated"));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error setting up TOTP", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Verifica el código TOTP y habilita TOTP para el usuario si es correcto
     */
    @Operation(
            summary = "Verificar codigo TOTP",
            description = "Valida el codigo proporcionado y habilita TOTP para el usuario autenticado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado de la verificacion", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "400", description = "Codigo invalido"),
            @ApiResponse(responseCode = "401", description = "Token invalido"),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/totp/verify")
    public ResponseEntity<Map<String, Object>> verifyAndEnableTotp(
            @Valid @RequestBody TotpVerifyRequest req,
            Authentication authentication,
            HttpServletRequest http) {
        try {
            String uid = authentication.getName();
            log.info("TOTP verification requested by user: {}", uid);

            boolean success = userService.verifyAndEnableTotp(uid, req.getCode());

            if (success) {
                audit.log(uid, uid, AuditAction.CREDENTIALS_UPDATED, http,
                        Map.of("action", "totp_enabled"));
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "TOTP enabled successfully"
                ));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "success", false,
                        "message", "Invalid TOTP code"
                ));
            }
        } catch (Exception e) {
            log.error("Error verifying TOTP", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Deshabilita TOTP para el usuario (requiere código válido para confirmar)
     */
    @Operation(
            summary = "Deshabilitar TOTP",
            description = "Desactiva TOTP para el usuario autenticado tras validar el codigo."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado de la desactivacion", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "400", description = "Codigo invalido"),
            @ApiResponse(responseCode = "401", description = "Token invalido"),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/totp/disable")
    public ResponseEntity<Map<String, Object>> disableTotp(
            @Valid @RequestBody TotpVerifyRequest req,
            Authentication authentication,
            HttpServletRequest http) {
        try {
            String uid = authentication.getName();
            log.info("TOTP disable requested by user: {}", uid);

            boolean success = userService.disableTotp(uid, req.getCode());

            if (success) {
                audit.log(uid, uid, AuditAction.CREDENTIALS_UPDATED, http,
                        Map.of("action", "totp_disabled"));
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "TOTP disabled successfully"
                ));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "success", false,
                        "message", "Invalid TOTP code"
                ));
            }
        } catch (Exception e) {
            log.error("Error disabling TOTP", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Login con verificación TOTP (segunda etapa del login)
     * Se llama después del login normal cuando el usuario tiene TOTP habilitado
     */
    @Operation(
            summary = "Completar login con TOTP",
            description = "Valida el codigo TOTP durante la segunda etapa del login.",
            parameters = {
                    @Parameter(name = "uid", description = "Identificador de sesion temporal", required = true)
            }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login completado", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "401", description = "Codigo TOTP invalido"),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @PostMapping("/login/totp")
    public ResponseEntity<LoginResponse> loginWithTotp(@Valid @RequestBody TotpVerifyRequest req,
                                                       @RequestParam String uid,
                                                       HttpServletRequest http) {
        try {
            log.info("TOTP login verification for user: {}", uid);

            LoginResponse response = userService.loginWithTotp(uid, req.getCode());

            audit.log(uid, uid, AuditAction.LOGIN, http,
                    Map.of("method", "totp"));

            log.info("Login with TOTP successful for user: {}", uid);
            return ResponseEntity.ok(response);

        } catch (UserProfileService.LoginFailedException e) {
            log.warn("TOTP login failed for user: {} - Reason: {}", uid, e.getReason());

            Map<String, Object> meta = Map.of(
                    "uid", uid,
                    "reason", e.getReason(),
                    "message", e.getMessage()
            );

            try {
                audit.log(uid, uid, AuditAction.LOGIN_FAILED, http, meta);
            } catch (Exception auditException) {
                log.error("Failed to log audit for failed TOTP login", auditException);
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        } catch (Exception e) {
            log.error("Unexpected error during TOTP login for user: {}", uid, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Consulta si el usuario tiene TOTP habilitado
     */
    @Operation(
            summary = "Consultar estado de TOTP",
            description = "Devuelve si el usuario autenticado tiene TOTP habilitado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado obtenido", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "Token invalido"),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/totp/status")
    public ResponseEntity<Map<String, Boolean>> getTotpStatus(Authentication authentication) {
        try {
            String uid = authentication.getName();
            boolean enabled = userService.getTotpEnabled(uid);
            return ResponseEntity.ok(Map.of("totpEnabled", enabled));
        } catch (Exception e) {
            log.error("Error getting TOTP status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== ENDPOINTS BIOMETRÍA ====================

    // DTO para preferencia biométrica
    class BiometricPreferenceDto {
        public boolean enabled;
        public BiometricPreferenceDto() {}
        public BiometricPreferenceDto(boolean enabled) { this.enabled = enabled; }
    }

    // ENDPOINT: Actualizar preferencia biométrica (usuario autenticado)
    @Operation(
            summary = "Actualizar preferencia biometrica",
            description = "Activa o desactiva la autenticacion biometrica para el usuario logueado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferencia actualizada"),
            @ApiResponse(responseCode = "401", description = "Token invalido"),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/biometric")
    public ResponseEntity<Void> updateBiometric(@RequestBody BiometricPreferenceDto dto, Authentication authentication) throws Exception {
        String uid = authentication.getName();
        userService.updateBiometricPreference(uid, dto.enabled);
        return ResponseEntity.ok().build();
    }

    // ENDPOINT: Consultar preferencia biométrica (usuario autenticado o admin)
    @Operation(
            summary = "Obtener preferencia biometrica",
            description = "Permite a un administrador o al propio usuario consultar si tiene habilitada la autenticacion biometrica.",
            parameters = {
                    @Parameter(name = "uid", description = "Identificador del usuario en Firebase", required = true)
            }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferencia obtenida", content = @Content(schema = @Schema(implementation = BiometricPreferenceDto.class))),
            @ApiResponse(responseCode = "401", description = "Token invalido"),
            @ApiResponse(responseCode = "403", description = "Permisos insuficientes"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{uid}/biometric")
    public ResponseEntity<BiometricPreferenceDto> getBiometric(@PathVariable String uid, Authentication authentication) throws Exception {
        // Permitir solo al propio usuario o admin
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !authentication.getName().equals(uid)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        boolean enabled = userService.getBiometricPreference(uid);
        return ResponseEntity.ok(new BiometricPreferenceDto(enabled));
    }

    // ENDPOINT: Listar estado biométrico de todos los usuarios (solo admin)
    @Operation(
            summary = "Listar estado biometrico de usuarios",
            description = "Devuelve el estado de autenticacion biometrica de todos los usuarios. Requiere rol ADMIN."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estados obtenidos", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Map.class)))),
            @ApiResponse(responseCode = "401", description = "Token invalido"),
            @ApiResponse(responseCode = "403", description = "Permisos insuficientes"),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/biometric-status")
    public ResponseEntity<List<Map<String, Object>>> getAllBiometricStatus() throws Exception {
        return ResponseEntity.ok(userService.getAllUsersBiometricStatus());
    }

    private String principalUid(Authentication auth) {
        return (auth != null && auth.getPrincipal() != null) ? auth.getPrincipal().toString() : null;
    }

    @Operation(
            summary = "Registrar logout",
            description = "Loguea manualmente el evento de cierre de sesion en el servicio de auditoria."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evento registrado", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @PostMapping("/audit/logout")
    public ResponseEntity<Map<String, String>> auditLogout(HttpServletRequest http, Authentication auth) {
        try {
            String userId = principalUid(auth);

            // Validar que hay un usuario autenticado
            if (userId == null || userId.isEmpty()) {
                log.warn("Logout attempt without valid authentication. Auth object: {}", auth);
                return ResponseEntity.status(401)
                        .body(Map.of("message", "No authenticated user to logout"));
            }

            log.info("User logout: {}", userId);
            audit.log(userId, userId, AuditAction.LOGOUT, http, null);
            return ResponseEntity.ok(Map.of("message", "Logout successful"));
        } catch (Exception e) {
            log.error("Error during logout audit", e);
            // No fallar el logout si hay error en auditoría
            return ResponseEntity.status(500)
                    .body(Map.of("message", "Error during logout", "details", e.getMessage()));
        }
    }

    @Operation(
            summary = "Registrar login fallido",
            description = "Permite registrar manualmente un intento de login fallido, por ejemplo desde el frontend.",
            parameters = {
                    @Parameter(name = "email", description = "Email del usuario que intento iniciar sesion", required = true),
                    @Parameter(name = "reason", description = "Descripcion corta de la razon del fallo", required = true)
            }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evento registrado", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @PostMapping("/audit/login-failed")
    public ResponseEntity<Map<String, String>> auditLoginFailed(@RequestParam String email,
                                                                @RequestParam String reason,
                                                                HttpServletRequest http) {
        try {
            log.warn("Manual login failed audit for email: {} - Reason: {}", email, reason);
            Map<String, Object> meta = Map.of("email", email, "reason", reason);
            audit.log(null, null, AuditAction.LOGIN_FAILED, http, meta);
            return ResponseEntity.ok(Map.of("message", "Audit logged"));
        } catch (Exception e) {
            log.error("Error logging failed login audit", e);
            return ResponseEntity.ok(Map.of("message", "Audit skipped"));
        }
    }
}
