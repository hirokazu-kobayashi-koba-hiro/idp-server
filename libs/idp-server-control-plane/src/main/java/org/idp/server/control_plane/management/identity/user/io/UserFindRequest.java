package org.idp.server.control_plane.management.identity.user.io;

import java.util.Map;
import org.idp.server.control_plane.management.identity.user.handler.UserManagementRequest;
import org.idp.server.core.openid.identity.UserIdentifier;

public class UserFindRequest implements UserManagementRequest {

  UserIdentifier userIdentifier;

  public UserFindRequest(UserIdentifier userIdentifier) {
    this.userIdentifier = userIdentifier;
  }

  public UserIdentifier userIdentifier() {
    return userIdentifier;
  }

  @Override
  public Map<String, Object> toMap() {
    return Map.of();
  }
}
