package org.idp.server.core.token;

import org.idp.server.basic.random.RandomStringGenerator;
import org.idp.server.core.type.oauth.RefreshToken;

public interface RefreshTokenCreatable {

  default RefreshToken createRefreshToken() {
    RandomStringGenerator randomStringGenerator = new RandomStringGenerator(24);
    String code = randomStringGenerator.generate();
    return new RefreshToken(code);
  }
}
