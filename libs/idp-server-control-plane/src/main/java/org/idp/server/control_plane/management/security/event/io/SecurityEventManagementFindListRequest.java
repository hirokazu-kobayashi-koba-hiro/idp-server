package org.idp.server.control_plane.management.security.event.io;

import java.util.Map;
import org.idp.server.platform.security.SecurityEventQueries;

public record SecurityEventManagementFindListRequest(SecurityEventQueries queries)
    implements SecurityEventManagementRequest {
  @Override
  public Map<String, Object> toMap() {
    return queries.toMap();
  }
}
