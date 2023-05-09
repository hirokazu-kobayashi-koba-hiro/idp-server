package org.idp.server.oauth.verifier.base;

import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.exception.OAuthBadRequestException;
import org.idp.server.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.type.OAuthRequestKey;

public class OidcRequestBaseVerifier implements AuthorizationRequestVerifier {

  OAuthRequestBaseVerifier baseVerifier = new OAuthRequestBaseVerifier();

  @Override
  public void verify(OAuthRequestContext context) {
    baseVerifier.verify(context);
    throwIfNotContainsRedirectUri(context);
    throwIfInvalidRedirectUri(context);
    throwIfInvalidDisplay(context);
    throwIfInvalidPrompt(context);
    throwIfInvalidMaxAge(context);
  }

  void throwIfNotContainsRedirectUri(OAuthRequestContext context) {
    if (!context.hasRedirectUri()) {
      throw new OAuthBadRequestException(
          "invalid_request", "oidc profile authorization request must contains redirect_uri param");
    }
  }

  void throwIfInvalidRedirectUri(OAuthRequestContext context) {
    if (!context.isRegisteredRedirectUri()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "authorization request redirect_uri does not register in client configuration (%s)",
              context.redirectUri().value()));
    }
  }

  void throwIfInvalidDisplay(OAuthRequestContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    if (authorizationRequest.isInvalidDisplay()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          String.format(
              "authorization request display is defined that page, popup, touch, wap, but request display is (%s)",
              context.getParams(OAuthRequestKey.display)),
          context);
    }
  }

  void throwIfInvalidPrompt(OAuthRequestContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    if (authorizationRequest.isInvalidPrompt()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          String.format(
              "authorization request prompt is defined that none, login, consent, select_account, but request prompt is (%s)",
              context.getParams(OAuthRequestKey.prompt)),
          context);
    }
  }

  void throwIfInvalidMaxAge(OAuthRequestContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    if (authorizationRequest.isInvalidMaxAge()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          String.format(
              "authorization request max_age is invalid (%s)",
              context.getParams(OAuthRequestKey.max_age)),
          context);
    }
  }
}
