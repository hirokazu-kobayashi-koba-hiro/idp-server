package org.idp.server.control_plane.management.identity.user.io;

import java.util.Map;
import org.idp.server.control_plane.management.identity.user.handler.UserManagementRequest;
import org.idp.server.core.openid.identity.UserQueries;

public class UserFindListRequest implements UserManagementRequest {

  UserQueries userQueries;

  public UserFindListRequest(UserQueries userQueries) {
    this.userQueries = userQueries;
  }

  public UserQueries userQueries() {
    return userQueries;
  }

  @Override
  public Map<String, Object> toMap() {
    return userQueries.toMap();
  }
}
