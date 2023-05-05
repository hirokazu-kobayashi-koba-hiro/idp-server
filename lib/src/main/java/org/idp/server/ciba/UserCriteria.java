package org.idp.server.ciba;

import org.idp.server.type.ciba.LoginHintToken;
import org.idp.server.type.oidc.IdTokenHint;
import org.idp.server.type.oidc.LoginHint;

// FIXME
public class UserCriteria {
  LoginHint loginHint;
  LoginHintToken loginHintToken;
  IdTokenHint idTokenHint;

  public UserCriteria(LoginHint loginHint, LoginHintToken loginHintToken, IdTokenHint idTokenHint) {
    this.loginHint = loginHint;
    this.loginHintToken = loginHintToken;
    this.idTokenHint = idTokenHint;
  }

  public LoginHint loginHint() {
    return loginHint;
  }

  public LoginHintToken loginHintToken() {
    return loginHintToken;
  }

  public IdTokenHint idTokenHint() {
    return idTokenHint;
  }
}
