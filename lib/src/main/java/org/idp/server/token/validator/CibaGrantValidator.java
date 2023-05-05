package org.idp.server.token.validator;

import org.idp.server.token.TokenRequestContext;
import org.idp.server.token.exception.TokenBadRequestException;
import org.idp.server.type.oauth.GrantType;

public class CibaGrantValidator {

  public void validate(TokenRequestContext tokenRequestContext) {
    throwIfUnSupportedGrantTypeWithServer(tokenRequestContext);
    throwIfUnSupportedGrantTypeWithClient(tokenRequestContext);
    throwIfNotContainsAuthReqId(tokenRequestContext);
    throwIfNotContainsClientId(tokenRequestContext);
  }

  void throwIfUnSupportedGrantTypeWithClient(TokenRequestContext tokenRequestContext) {
    if (!tokenRequestContext.isSupportedGrantTypeWithClient(GrantType.ciba)) {
      throw new TokenBadRequestException(
          "this request grant_type is ciba, but client does not support");
    }
  }

  void throwIfUnSupportedGrantTypeWithServer(TokenRequestContext tokenRequestContext) {
    if (!tokenRequestContext.isSupportedGrantTypeWithServer(GrantType.ciba)) {
      throw new TokenBadRequestException(
          "this request grant_type is ciba, but authorization server does not support");
    }
  }

  void throwIfNotContainsAuthReqId(TokenRequestContext tokenRequestContext) {
    if (!tokenRequestContext.hasAuthReqId()) {
      throw new TokenBadRequestException(
          "token request does not contains code, ciba grant must contains auth_req_id");
    }
  }

  void throwIfNotContainsClientId(TokenRequestContext tokenRequestContext) {
    if (tokenRequestContext.hasClientSecretBasic()) {
      return;
    }
    if (!tokenRequestContext.hasClientId()) {
      throw new TokenBadRequestException(
          "token request does not contains client_id, ciba grant must contains client_id");
    }
  }
}
