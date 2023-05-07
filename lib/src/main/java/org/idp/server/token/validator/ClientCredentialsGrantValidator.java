package org.idp.server.token.validator;

import org.idp.server.token.TokenRequestContext;
import org.idp.server.token.exception.TokenBadRequestException;
import org.idp.server.type.oauth.GrantType;

public class ClientCredentialsGrantValidator {

  TokenRequestContext tokenRequestContext;

  public ClientCredentialsGrantValidator(TokenRequestContext tokenRequestContext) {
    this.tokenRequestContext = tokenRequestContext;
  }

  public void validate() {
    throwIfUnSupportedGrantTypeWithServer();
    throwIfUnSupportedGrantTypeWithClient();
  }

  void throwIfUnSupportedGrantTypeWithClient() {
    if (!tokenRequestContext.isSupportedGrantTypeWithClient(GrantType.client_credentials)) {
      throw new TokenBadRequestException(
          "this request grant_type is client_credentials, but client does not support");
    }
  }

  void throwIfUnSupportedGrantTypeWithServer() {
    if (!tokenRequestContext.isSupportedGrantTypeWithServer(GrantType.client_credentials)) {
      throw new TokenBadRequestException(
          "this request grant_type is client_credentials, but authorization server does not support");
    }
  }
}
