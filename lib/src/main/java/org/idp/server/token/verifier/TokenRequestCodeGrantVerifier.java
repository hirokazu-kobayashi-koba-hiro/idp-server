package org.idp.server.token.verifier;

import org.idp.server.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.token.TokenRequestContext;
import org.idp.server.token.exception.TokenBadRequestException;
import org.idp.server.type.oauth.GrantType;

public class TokenRequestCodeGrantVerifier {

  public void validate(
      TokenRequestContext tokenRequestContext, AuthorizationCodeGrant authorizationCodeGrant) {
    throwIfUnSupportedGrantTypeWithServer(tokenRequestContext);
    throwIfUnSupportedGrantTypeWithClient(tokenRequestContext);
  }

  void throwIfNotFoundAuthorizationCode(
      TokenRequestContext tokenRequestContext, AuthorizationCodeGrant authorizationCodeGrant) {
    if (!authorizationCodeGrant.exists()) {
      throw new TokenBadRequestException("");
    }
  }

  void throwIfUnSupportedGrantTypeWithClient(TokenRequestContext tokenRequestContext) {
    if (!tokenRequestContext.isSupportedGrantTypeWithClient(GrantType.authorization_code)) {
      throw new TokenBadRequestException(
          "this request grant_type is authorization_code, but client does not support");
    }
  }

  void throwIfUnSupportedGrantTypeWithServer(TokenRequestContext tokenRequestContext) {
    if (!tokenRequestContext.isSupportedGrantTypeWithServer(GrantType.authorization_code)) {
      throw new TokenBadRequestException(
          "this request grant_type is authorization_code, but authorization server does not support");
    }
  }
}
