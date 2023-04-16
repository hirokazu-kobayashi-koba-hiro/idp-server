package org.idp.server.oauth.verifier.base;

import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.type.OAuthRequestKey;
import org.idp.server.type.oauth.ResponseType;
import org.idp.server.type.oauth.Scopes;

public class OAuthRequestBaseVerifier implements AuthorizationRequestVerifier {

  @Override
  public void verify(OAuthRequestContext context) {
    throwIfInvalidResponseType(context);
    throwIfUnSupportedResponseType(context);
    throwIfNotContainsValidScope(context);
  }

  void throwIfInvalidResponseType(OAuthRequestContext context) {
    ResponseType responseType = context.responseType();
    if (responseType.isUndefined()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request", "response type is required on authorization request", context);
    }
    if (responseType.isUnknown()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          String.format(
              "response type is unknown type (%s)",
              context.getParams(OAuthRequestKey.response_type)),
          context);
    }
  }

  void throwIfUnSupportedResponseType(OAuthRequestContext context) {
    ResponseType responseType = context.responseType();
    if (!context.isSupportedResponseTypeWithServer()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          String.format(
              "authorization server is unsupported response_type (%s)", responseType.name()),
          context);
    }

    if (!context.isSupportedResponseTypeWithClient()) {
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
          "invalid_scope",
          String.format(
              "authorization request does not contains valid scope (%s)",
              context.getParams(OAuthRequestKey.scope)),
          context);
    }
  }
}
