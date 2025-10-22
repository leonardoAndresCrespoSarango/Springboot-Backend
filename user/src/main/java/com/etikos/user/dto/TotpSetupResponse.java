package com.etikos.user.dto;

/**
 * Respuesta al iniciar la configuración de TOTP
 * Contiene el QR code y el secreto manual para configurar Google Authenticator
 */
public class TotpSetupResponse {
    private String secret;
    private String qrCodeDataUri;

    public TotpSetupResponse() {}

    public TotpSetupResponse(String secret, String qrCodeDataUri) {
        this.secret = secret;
        this.qrCodeDataUri = qrCodeDataUri;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getQrCodeDataUri() {
        return qrCodeDataUri;
    }

    public void setQrCodeDataUri(String qrCodeDataUri) {
        this.qrCodeDataUri = qrCodeDataUri;
    }
}

