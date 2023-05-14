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
    throwExceptionIfUnSupportedGrantTypeWithServer();
    throwExceptionIfUnSupportedGrantTypeWithClient();
  }

  void throwExceptionIfUnSupportedGrantTypeWithClient() {
    if (!tokenRequestContext.isSupportedGrantTypeWithClient(GrantType.password)) {
      throw new TokenBadRequestException(
          "unsupported_grant_type",
          "this request grant_type is password, but client does not support");
    }
    if (!tokenRequestContext.isSupportedPasswordGrant()) {
      throw new TokenBadRequestException(
          "unsupported_grant_type",
          "this request grant_type is client_credentials, but client does not support");
    }
  }

  void throwExceptionIfUnSupportedGrantTypeWithServer() {
    if (!tokenRequestContext.isSupportedGrantTypeWithServer(GrantType.password)) {
      throw new TokenBadRequestException(
          "unauthorized_client",
          "this request grant_type is client_credentials, but authorization server does not authorize");
    }
  }
}
