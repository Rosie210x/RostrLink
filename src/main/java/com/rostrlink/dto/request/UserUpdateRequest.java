package com.rostrlink.dto.request;

import com.rostrlink.common.UserRole;
import com.rostrlink.common.UserStatus;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * PUT /users/{id} — full update.
 * Password is optional; when provided it must satisfy the complexity policy.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class UserUpdateRequest {

    @Size(min = 8, max = 128, message = "Mật khẩu phải có từ {min} đến {max} ký tự")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#+\\-_])[A-Za-z\\d@$!%*?&#+\\-_]{8,}$",
            message = "Mật khẩu phải chứa ít nhất 1 chữ hoa, 1 chữ thường, 1 chữ số và 1 ký tự đặc biệt"
    )
    private String password;

    @NotBlank(message = "Tên là bắt buộc")
    @Pattern(
            regexp = "^(\\p{Lu}\\p{Ll}+)(\\s\\p{Lu}\\p{Ll}+)*$",
            message = "Mỗi từ phải bắt đầu hoa, chỉ chứa chữ, không số/ký tự đặc biệt, không khoảng trắng thừa"
    )
    private String firstName;

    @NotBlank(message = "Họ là bắt buộc")
    @Pattern(
            regexp = "^(\\p{Lu}\\p{Ll}+)(\\s\\p{Lu}\\p{Ll}+)*$",
            message = "Mỗi từ phải bắt đầu hoa, chỉ chứa chữ, không số/ký tự đặc biệt, không khoảng trắng thừa"
    )
    private String lastName;

    @Email(message = "Định dạng email không hợp lệ")
    @NotBlank(message = "Email là bắt buộc")
    private String email;

    @Pattern(
            regexp = "^(0\\d{9})?$",
            message = "Số điện thoại phải có 10 chữ số và có định dạng 0xxxxxxxxx"
    )
    private String phoneNumber;

    @NotNull(message = "Vai trò là bắt buộc")
    private UserRole userRole;

    private UserStatus status;

    private String avatarUrl;
}