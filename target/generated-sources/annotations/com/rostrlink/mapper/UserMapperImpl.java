package com.rostrlink.mapper;

import com.rostrlink.common.UserStatus;
import com.rostrlink.dto.request.UserCreateRequest;
import com.rostrlink.dto.request.UserUpdateRequest;
import com.rostrlink.dto.response.UserResponse;
import com.rostrlink.entity.auth.User;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-20T15:52:45+0700",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 25.0.1 (Oracle Corporation)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public User toUser(UserCreateRequest request) {
        if ( request == null ) {
            return null;
        }

        User.UserBuilder user = User.builder();

        user.email( request.getEmail() );
        user.firstName( request.getFirstName() );
        user.lastName( request.getLastName() );
        user.phoneNumber( request.getPhoneNumber() );
        user.avatarUrl( request.getAvatarUrl() );
        if ( request.getStatus() != null ) {
            user.status( request.getStatus().name() );
        }

        return user.build();
    }

    @Override
    public void updateUserFromRequest(UserUpdateRequest request, User user) {
        if ( request == null ) {
            return;
        }

        if ( request.getUserId() != null ) {
            user.setUserId( request.getUserId().longValue() );
        }
        else {
            user.setUserId( null );
        }
        user.setEmail( request.getEmail() );
        user.setFirstName( request.getFirstName() );
        user.setLastName( request.getLastName() );
        user.setPhoneNumber( request.getPhoneNumber() );
        if ( request.getStatus() != null ) {
            user.setStatus( request.getStatus().name() );
        }
        else {
            user.setStatus( null );
        }
    }

    @Override
    public UserResponse toResponse(User user) {
        if ( user == null ) {
            return null;
        }

        UserResponse.UserResponseBuilder userResponse = UserResponse.builder();

        if ( user.getUserId() != null ) {
            userResponse.userId( user.getUserId().intValue() );
        }
        userResponse.firstName( user.getFirstName() );
        userResponse.lastName( user.getLastName() );
        userResponse.email( user.getEmail() );
        userResponse.phoneNumber( user.getPhoneNumber() );
        if ( user.getStatus() != null ) {
            userResponse.status( Enum.valueOf( UserStatus.class, user.getStatus() ) );
        }

        return userResponse.build();
    }

    @Override
    public List<UserResponse> toResponseList(List<User> users) {
        if ( users == null ) {
            return null;
        }

        List<UserResponse> list = new ArrayList<UserResponse>( users.size() );
        for ( User user : users ) {
            list.add( toResponse( user ) );
        }

        return list;
    }
}
