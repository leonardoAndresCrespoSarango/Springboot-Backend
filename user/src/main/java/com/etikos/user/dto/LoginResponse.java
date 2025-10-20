package com.etikos.user.dto;

public class LoginResponse {

    private String token;
    private UserProfileDto user;

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
}

