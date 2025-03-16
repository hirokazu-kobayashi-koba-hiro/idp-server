package org.idp.server.adapters.springboot.restapi.management;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.idp.server.core.oauth.identity.User;

public class UserListResponse {
  @JsonProperty("list")
  List<UserResponse> list;

  public UserListResponse(List<User> userList) {
    this.list = userList.stream().map(UserResponse::new).toList();
  }
}
