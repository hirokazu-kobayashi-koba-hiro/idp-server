package org.idp.server.ciba.service;

import org.idp.server.ciba.CibaRequestContext;
import org.idp.server.ciba.CibaRequestDelegate;
import org.idp.server.ciba.UserCriteria;
import org.idp.server.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.oauth.identity.User;

public class UserService {
  CibaRequestDelegate cibaRequestDelegate;
  CibaRequestContext context;

  public UserService(CibaRequestDelegate cibaRequestDelegate, CibaRequestContext context) {
    this.cibaRequestDelegate = cibaRequestDelegate;
    this.context = context;
  }

  public User handle() {
    BackchannelAuthenticationRequest backchannelAuthenticationRequest =
        context.backchannelAuthenticationRequest();
    User user =
        cibaRequestDelegate.find(
            new UserCriteria(
                backchannelAuthenticationRequest.loginHint(),
                backchannelAuthenticationRequest.loginHintToken(),
                backchannelAuthenticationRequest.idTokenHint()));
    if (!user.exists()) {
      throw new BackchannelAuthenticationBadRequestException(
          "unknown_user_id",
          "The OpenID Provider is not able to identify which end-user the Client wishes to be authenticated by means of the hint provided in the request (login_hint_token, id_token_hint, or login_hint).");
    }
    if (context.hasUserCode()) {
      boolean authenticationResult = cibaRequestDelegate.authenticate(user, context.userCode());
      if (!authenticationResult) {
        throw new BackchannelAuthenticationBadRequestException(
            "invalid_user_code", "backchannel authentication request user_code is invalid");
      }
    }
    cibaRequestDelegate.notify(user, backchannelAuthenticationRequest);
    return user;
  }
}
