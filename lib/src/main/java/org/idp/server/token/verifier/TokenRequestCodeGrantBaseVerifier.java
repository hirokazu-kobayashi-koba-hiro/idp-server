package org.idp.server.token.verifier;

import org.idp.server.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.token.TokenRequestContext;
import org.idp.server.token.exception.TokenBadRequestException;
import org.idp.server.token.exception.TokenInvalidGrantException;

public class TokenRequestCodeGrantBaseVerifier {

  public void verify(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant) {
    throwIfNotFoundAuthorizationCode(
        tokenRequestContext, authorizationRequest, authorizationCodeGrant);
    throwIfUnMatchRedirectUri(tokenRequestContext, authorizationRequest, authorizationCodeGrant);
  }

  void throwIfNotFoundAuthorizationCode(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant) {
    if (!authorizationCodeGrant.exists()) {
      throw new TokenInvalidGrantException(
          "invalid_grant",
          String.format("not found authorization code (%s)", tokenRequestContext.code().value()));
    }
    if (!authorizationRequest.exists()) {
      throw new TokenInvalidGrantException(
          "invalid_grant",
          String.format("not found authorization code (%s)", tokenRequestContext.code().value()));
    }
  }

  void throwIfUnMatchRedirectUri(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant) {
    if (!authorizationRequest.hasRedirectUri()) {
      return;
    }
    if (!authorizationRequest.redirectUri().equals(tokenRequestContext.redirectUri())) {
      throw new TokenBadRequestException(
          String.format(
              "token request redirect_uri does not equals to authorization request redirect_uri (%s)",
              tokenRequestContext.redirectUri().value()));
    }
  }
}
