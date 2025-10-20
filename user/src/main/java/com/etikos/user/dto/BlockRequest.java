package com.etikos.user.dto;

import jakarta.validation.constraints.NotNull;

public class BlockRequest {
    @NotNull private Boolean disabled; // true = bloquear, false = desbloquear

    public Boolean getDisabled() {
        return disabled;
    }

    public void setDisabled(Boolean disabled) {
        this.disabled = disabled;
    }
}