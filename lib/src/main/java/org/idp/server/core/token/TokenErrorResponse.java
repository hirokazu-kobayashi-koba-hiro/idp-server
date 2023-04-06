package org.idp.server.core.token;

import java.util.Map;
import org.idp.server.core.type.Error;
import org.idp.server.core.type.ErrorDescription;

public class TokenErrorResponse {
  Error error;
  ErrorDescription errorDescription;

  public TokenErrorResponse() {}

  public TokenErrorResponse(Error error, ErrorDescription errorDescription) {
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public Map<String, Object> response() {
    return Map.of("error", error.value(), "error_description", errorDescription.value());
  }
}
