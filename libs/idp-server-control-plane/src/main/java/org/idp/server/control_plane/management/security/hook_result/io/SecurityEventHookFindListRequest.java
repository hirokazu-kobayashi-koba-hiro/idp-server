package org.idp.server.control_plane.management.security.hook_result.io;

import java.util.Map;
import org.idp.server.platform.security.hook.SecurityEventHookResultQueries;

public record SecurityEventHookFindListRequest(SecurityEventHookResultQueries queries)
    implements SecurityEventHookManagementRequest {

  @Override
  public Map<String, Object> toMap() {
    return queries.toMap();
  }
}
