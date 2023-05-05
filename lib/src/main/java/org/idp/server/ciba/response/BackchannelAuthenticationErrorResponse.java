package org.idp.server.ciba.response;

import org.idp.server.type.oauth.Error;
import org.idp.server.type.oauth.ErrorDescription;

public class BackchannelAuthenticationErrorResponse {
  Error error;
  ErrorDescription errorDescription;

  public BackchannelAuthenticationErrorResponse() {}

  public BackchannelAuthenticationErrorResponse(Error error, ErrorDescription errorDescription) {
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public Error error() {
    return error;
  }

  public ErrorDescription errorDescription() {
    return errorDescription;
  }

  public String contents() {
    String format =
        """
                {
                  "error": "%s",
                  "error_description": "%s"
                }
                """;
    return String.format(format, error.value(), errorDescription.value());
  }
}
