package com.etikos.user.dto;

public class UpdateCredentialsRequest {
    private String newEmail;    // opcional
    private String newPassword; // opcional

    public String getNewEmail() {
        return newEmail;
    }

    public void setNewEmail(String newEmail) {
        this.newEmail = newEmail;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}