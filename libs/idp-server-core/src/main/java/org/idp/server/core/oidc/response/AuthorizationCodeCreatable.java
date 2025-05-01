package org.idp.server.core.oidc.response;

import org.idp.server.basic.random.RandomStringGenerator;
import org.idp.server.basic.type.oauth.AuthorizationCode;

public interface AuthorizationCodeCreatable {

  default AuthorizationCode createAuthorizationCode() {
    RandomStringGenerator randomStringGenerator = new RandomStringGenerator(20);
    return new AuthorizationCode(randomStringGenerator.generate());
  }
}
