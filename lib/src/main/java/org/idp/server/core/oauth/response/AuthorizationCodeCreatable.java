package org.idp.server.core.oauth.response;

import org.idp.server.basic.random.RandomStringGenerator;
import org.idp.server.core.type.oauth.AuthorizationCode;

public interface AuthorizationCodeCreatable {

  default AuthorizationCode createAuthorizationCode() {
    RandomStringGenerator randomStringGenerator = new RandomStringGenerator(20);
    return new AuthorizationCode(randomStringGenerator.generate());
  }
}
