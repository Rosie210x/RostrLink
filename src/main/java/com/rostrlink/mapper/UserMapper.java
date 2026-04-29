package com.rostrlink.mapper;

import com.rostrlink.dto.request.UserCreateRequest;
import com.rostrlink.dto.request.UserPatchRequest;
import com.rostrlink.dto.request.UserUpdateRequest;
import com.rostrlink.dto.response.UserResponse;
import com.rostrlink.entity.auth.User;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UserMapper {

    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "userId", ignore = true)
    User toUser(UserCreateRequest request);

    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "userUserRoles", ignore = true)
    void updateUserFromRequest(UserUpdateRequest request, @MappingTarget User user);

    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "userUserRoles", ignore = true)
    void patchUserFromRequest(UserPatchRequest request, @MappingTarget User user);

    @Mapping(target = "roles", ignore = true)
    UserResponse toResponse(User user);

    List<UserResponse> toResponseList(List<User> users);
}