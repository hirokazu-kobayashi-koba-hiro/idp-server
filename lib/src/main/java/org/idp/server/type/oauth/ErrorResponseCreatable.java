package org.idp.server.type.oauth;

public interface ErrorResponseCreatable {

  default String toErrorResponse(Error error, ErrorDescription errorDescription) {
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
