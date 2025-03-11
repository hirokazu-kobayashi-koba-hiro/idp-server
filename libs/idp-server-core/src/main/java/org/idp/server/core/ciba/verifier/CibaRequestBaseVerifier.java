package org.idp.server.core.ciba.verifier;

import org.idp.server.core.ciba.CibaRequestContext;
import org.idp.server.core.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.type.oauth.GrantType;

public class CibaRequestBaseVerifier {

  public void verify(CibaRequestContext context) {
    throwExceptionIfUnSupportedGrantType(context);
    throwExceptionIfNotContainsOpenidScope(context);
    throwExceptionIfNotContainsAnyHint(context);
  }

  void throwExceptionIfUnSupportedGrantType(CibaRequestContext context) {
    if (!context.isSupportedGrantTypeWithServer(GrantType.ciba)) {
      throw new BackchannelAuthenticationBadRequestException(
          "unauthorized_client", "authorization server is unsupported ciba grant");
    }
    if (!context.isSupportedGrantTypeWithClient(GrantType.ciba)) {
      throw new BackchannelAuthenticationBadRequestException(
          "unauthorized_client", "client is unauthorized ciba grant");
    }
  }

  void throwExceptionIfNotContainsOpenidScope(CibaRequestContext context) {
    if (!context.hasOpenidScope()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_scope",
          "backchannel request does not contains openid scope. OpenID Connect implements authentication as an extension to OAuth 2.0 by including the openid scope value in the authorization requests.");
    }
  }

  void throwExceptionIfNotContainsAnyHint(CibaRequestContext context) {
    if (!context.hasAnyHint()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request",
          "backchannel request does not have any hint, must contains login_hint or login_hint_token or id_token_hint");
    }
  }
}
