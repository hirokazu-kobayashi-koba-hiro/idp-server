package org.idp.server.core.oidc.userinfo;

import java.util.Map;
import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;

public class UserinfoErrorResponse {
  Error error;
  ErrorDescription errorDescription;

  public UserinfoErrorResponse() {}

  public UserinfoErrorResponse(Error error, ErrorDescription errorDescription) {
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public Map<String, Object> response() {
    return Map.of("error", error.value(), "error_description", errorDescription.value());
  }
}
