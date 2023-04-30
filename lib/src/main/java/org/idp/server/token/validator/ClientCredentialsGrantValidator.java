package org.idp.server.token.validator;

import org.idp.server.token.TokenRequestContext;
import org.idp.server.token.exception.TokenBadRequestException;
import org.idp.server.type.oauth.GrantType;

public class ClientCredentialsGrantValidator {

  public void validate(TokenRequestContext tokenRequestContext) {
    throwIfUnSupportedGrantTypeWithServer(tokenRequestContext);
    throwIfUnSupportedGrantTypeWithClient(tokenRequestContext);
  }

  void throwIfUnSupportedGrantTypeWithClient(TokenRequestContext tokenRequestContext) {
    if (!tokenRequestContext.isSupportedGrantTypeWithClient(GrantType.client_credentials)) {
      throw new TokenBadRequestException(
          "this request grant_type is client_credentials, but client does not support");
    }
  }

  void throwIfUnSupportedGrantTypeWithServer(TokenRequestContext tokenRequestContext) {
    if (!tokenRequestContext.isSupportedGrantTypeWithServer(GrantType.client_credentials)) {
      throw new TokenBadRequestException(
          "this request grant_type is client_credentials, but authorization server does not support");
    }
  }
}
