package org.idp.server.oauth.exception;

public enum OAuthError {
  invalid_request,
  unauthorized_client,
  access_denied,
  unsupported_response_type,
  invalid_scope,
  server_error,
  temporarily_unavailable,
}
