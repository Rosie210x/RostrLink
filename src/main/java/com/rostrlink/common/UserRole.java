package com.rostrlink.common;

import java.util.List;

public enum UserRole {
    ADMIN("Quản trị viên"),
    USER("Người dùng"),
    PARENT("Phụ huynh"),
    TEACHER("Giáo viên"),
    DRIVER("Ổ đĩa"),
    KIOSK("Kiosk");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static List<UserRole> getRoles() {
        return List.of(
                ADMIN,
                USER,
                PARENT,
                TEACHER,
                DRIVER,
                KIOSK
        );
    }
}
