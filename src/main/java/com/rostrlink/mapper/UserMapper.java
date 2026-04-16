package com.rostrlink.mapper;

import com.rostrlink.dto.request.UserCreateRequest;
import com.rostrlink.dto.request.UserUpdateRequest;
import com.rostrlink.dto.response.UserResponse;
import com.rostrlink.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {
    User toUser(UserCreateRequest request);

    @Mapping(target = "passwordHash", ignore = true)
    void updateUserFromRequest(UserUpdateRequest request, @MappingTarget User user);

    UserResponse toResponse(User user);

    List<UserResponse> toResponseList(List<User> users);
}