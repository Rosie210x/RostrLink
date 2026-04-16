package com.rostrlink.common;

public enum UserStatus {
    ACTIVE("Hoạt động"),
    INACTIVE("Không hoạt động"),
    BANNED("Tài khoản bị cấm"),
    PENDING_VERIFICATION("Tài khoản đang chờ phê duyệt");

    private final String description;
    UserStatus(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }
}
