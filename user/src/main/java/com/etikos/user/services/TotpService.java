package com.etikos.user.services;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static dev.samstevens.totp.util.Utils.getDataUriForImage;

/**
 * Servicio para manejar TOTP (Time-based One-Time Password)
 * Compatible con Google Authenticator, Authy, Microsoft Authenticator, etc.
 */
@Service
public class TotpService {

    private static final Logger log = LoggerFactory.getLogger(TotpService.class);
    private static final String ISSUER = "Etikos"; // Nombre de tu aplicación

    private final DefaultSecretGenerator secretGenerator;
    private final QrGenerator qrGenerator;
    private final CodeVerifier verifier;

    public TotpService() {
        this.secretGenerator = new DefaultSecretGenerator();
        this.qrGenerator = new ZxingPngQrGenerator();

        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        this.verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
    }

    /**
     * Genera un secreto TOTP aleatorio para un usuario
     */
    public String generateSecret() {
        String secret = secretGenerator.generate();
        log.debug("Generated new TOTP secret");
        return secret;
    }

    /**
     * Genera un QR code en formato Data URI que el usuario puede escanear con Google Authenticator
     *
     * @param secret El secreto TOTP del usuario
     * @param userEmail Email del usuario (se mostrará en la app authenticator)
     * @return Data URI del QR code (imagen PNG en base64)
     */
    public String generateQrCodeDataUri(String secret, String userEmail) throws QrGenerationException {
        QrData data = new QrData.Builder()
                .label(userEmail)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1) // Google Authenticator usa SHA1
                .digits(6) // 6 dígitos
                .period(30) // 30 segundos de validez
                .build();

        byte[] imageData = qrGenerator.generate(data);
        String dataUri = getDataUriForImage(imageData, qrGenerator.getImageMimeType());

        log.debug("Generated QR code for user: {}", userEmail);
        return dataUri;
    }

    /**
     * Verifica si un código TOTP es válido
     *
     * @param secret El secreto TOTP del usuario
     * @param code El código de 6 dígitos ingresado por el usuario
     * @return true si el código es válido, false en caso contrario
     */
    public boolean verifyCode(String secret, String code) {
        boolean isValid = verifier.isValidCode(secret, code);
        log.debug("TOTP code verification result: {}", isValid);
        return isValid;
    }

    /**
     * Verifica un código TOTP con ventana de tiempo extendida (permite códigos anteriores/siguientes)
     * Útil para compensar desfases de reloj entre servidor y cliente
     */
    public boolean verifyCodeWithWindow(String secret, String code) {
        // Permite 1 período antes y después (90 segundos total de ventana)
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        CodeVerifier verifierWithWindow = new DefaultCodeVerifier(codeGenerator, timeProvider);

        // Configurar ventana de discrepancia (1 = 30 segundos antes y después)
        ((DefaultCodeVerifier) verifierWithWindow).setTimePeriod(30);
        ((DefaultCodeVerifier) verifierWithWindow).setAllowedTimePeriodDiscrepancy(1);

        boolean isValid = verifierWithWindow.isValidCode(secret, code);
        log.debug("TOTP code verification with window result: {}", isValid);
        return isValid;
    }
}

