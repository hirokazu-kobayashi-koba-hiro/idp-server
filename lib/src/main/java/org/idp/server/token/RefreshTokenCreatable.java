package org.idp.server.token;

import org.idp.server.basic.random.RandomStringGenerator;
import org.idp.server.type.oauth.RefreshToken;

public interface RefreshTokenCreatable {

  default RefreshToken createRefreshToken() {
    RandomStringGenerator randomStringGenerator = new RandomStringGenerator(24);
    String code = randomStringGenerator.generate();
    return new RefreshToken(code);
  }
}
