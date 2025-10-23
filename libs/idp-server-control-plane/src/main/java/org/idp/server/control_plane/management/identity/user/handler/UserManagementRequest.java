package org.idp.server.control_plane.management.identity.user.handler;

import java.util.Map;

public interface UserManagementRequest {
  Map<String, Object> toMap();
}
