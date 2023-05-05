package org.idp.server.ciba;

import org.idp.server.type.ciba.LoginHintToken;
import org.idp.server.type.oidc.IdTokenHint;
import org.idp.server.type.oidc.LoginHint;

public class UserCriteria {
  LoginHint loginHint;
  LoginHintToken loginHintToken;
  IdTokenHint idTokenHint;
}
