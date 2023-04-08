package org.idp.server.core.oauth.verifier;

import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.type.oauth.ResponseType;
import org.idp.server.core.type.oauth.Scopes;

public class OAuth2RequestVerifier implements AuthorizationRequestVerifier {
  @Override
  public void verify(OAuthRequestContext context) {
    throwIfInvalidRedirectUri(context);
    throwIfUnSupportedResponseType(context);
    throwIfNotContainsValidScope(context);
  }

  void throwIfInvalidRedirectUri(OAuthRequestContext context) {}

  void throwIfUnSupportedResponseType(OAuthRequestContext context) {
    ResponseType responseType = context.responseType();
    if (!context.isSupportedResponseTypeWithServer(responseType)) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          String.format(
              "authorization server is unsupported response_type (%s)", responseType.name()),
          context);
    }

    if (!context.isSupportedResponseTypeWithClient(responseType)) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          String.format("client is unsupported response_type (%s)", responseType.name()),
          context);
    }
  }

  void throwIfNotContainsValidScope(OAuthRequestContext context) {
    Scopes scopes = context.scopes();
    if (!scopes.exists()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request", "authorization request does not contains valid scope", context);
    }
  }
}
