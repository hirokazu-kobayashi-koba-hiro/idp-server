package org.idp.server.type.oauth;

/**
 * 4.1.2.1. Error Response
 *
 * <p>If the request fails due to a missing, invalid, or mismatching redirection URI, or if the
 * client identifier is missing or invalid, the authorization server SHOULD inform the resource
 * owner of the error and MUST NOT automatically redirect the user-agent to the invalid redirection
 * URI.
 *
 * <p>If the resource owner denies the access request or if the request fails for reasons other than
 * a missing or invalid redirection URI, the authorization server informs the client by adding the
 * following parameters to the query component of the redirection URI using the
 * "application/x-www-form-urlencoded" format, per Appendix B:
 */
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
