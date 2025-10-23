package org.idp.server.control_plane.management.security.hook_result.io;

import java.util.Map;
import org.idp.server.platform.security.hook.SecurityEventHookResultIdentifier;

public record SecurityEventHookRetryRequest(
    SecurityEventHookResultIdentifier securityEventHookResultIdentifier)
    implements SecurityEventHookManagementRequest {

  @Override
  public Map<String, Object> toMap() {
    return Map.of();
  }
}
