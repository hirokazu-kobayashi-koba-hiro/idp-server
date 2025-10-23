package org.idp.server.control_plane.management.security.hook_result.io;

import java.util.Map;

public interface SecurityEventHookManagementRequest {

  Map<String, Object> toMap();
}
