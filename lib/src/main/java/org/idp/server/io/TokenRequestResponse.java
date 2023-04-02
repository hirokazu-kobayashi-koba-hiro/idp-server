package org.idp.server.io;

import java.util.Map;
import org.idp.server.core.oauth.token.TokenResponse;
import org.idp.server.io.status.TokenRequestStatus;

public class TokenRequestResponse {
  TokenRequestStatus status;
  TokenResponse tokenResponse;
  Map<String, Object> response;

  public Map<String, Object> response() {
    if (status.isOK()) {
      return Map.of();
    }
    return Map.of();
  }
}
