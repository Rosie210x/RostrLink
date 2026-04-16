package com.rostrlink.common;

import java.util.List;

public enum Role {
    ADMIN("Quản trị viên"),
    USER("Người dùng"),
    PARENT("Phụ huynh"),
    TEACHER("Giáo viên"),
    DRIVER("Ổ đĩa"),
    KIOSK("Kiosk");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static List<Role> getRoles() {
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
