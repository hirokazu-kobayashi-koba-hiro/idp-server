package org.idp.server.control_plane.management.security.event.io;

import java.util.Map;

public interface SecurityEventManagementRequest {

  Map<String, Object> toMap();
}
