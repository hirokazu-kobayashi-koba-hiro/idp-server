package org.idp.sample;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.idp.server.oauth.identity.User;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class UserListResponse {
    @JsonProperty("list")
    List<UserResponse> list;

    public UserListResponse(List<User> userList) {
        this.list = userList.stream().map(UserResponse::new).toList();
    }
}
