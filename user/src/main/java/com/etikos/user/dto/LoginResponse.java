package com.etikos.user.dto;

public class LoginResponse {

    private String token;
    private UserProfileDto user;
    private boolean totpRequired; // Indica si el usuario debe proporcionar código TOTP
    private String tempSessionId; // ID de sesión temporal antes de validar TOTP

    // Constructors
    public LoginResponse() {
    }

    public LoginResponse(String token, UserProfileDto user) {
        this.token = token;
        this.user = user;
    }

    // Getters and Setters
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserProfileDto getUser() {
        return user;
    }

    public void setUser(UserProfileDto user) {
        this.user = user;
    }

    public boolean isTotpRequired() {
        return totpRequired;
    }

    public void setTotpRequired(boolean totpRequired) {
        this.totpRequired = totpRequired;
    }

    public String getTempSessionId() {
        return tempSessionId;
    }

    public void setTempSessionId(String tempSessionId) {
        this.tempSessionId = tempSessionId;
    }
}

