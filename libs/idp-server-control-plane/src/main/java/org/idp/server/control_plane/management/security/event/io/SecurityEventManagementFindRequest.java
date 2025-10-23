package org.idp.server.control_plane.management.security.event.io;

import java.util.Map;
import org.idp.server.platform.security.event.SecurityEventIdentifier;

public record SecurityEventManagementFindRequest(SecurityEventIdentifier securityEventIdentifier)
    implements SecurityEventManagementRequest {
  @Override
  public Map<String, Object> toMap() {
    return Map.of();
  }
}
