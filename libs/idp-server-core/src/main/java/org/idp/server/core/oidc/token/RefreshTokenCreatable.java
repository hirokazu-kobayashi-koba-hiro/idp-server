package org.idp.server.core.oidc.token;

import java.time.LocalDateTime;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.basic.random.RandomStringGenerator;
import org.idp.server.basic.type.extension.CreatedAt;
import org.idp.server.basic.type.extension.ExpiredAt;
import org.idp.server.basic.type.oauth.RefreshTokenEntity;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;

public interface RefreshTokenCreatable {

  default RefreshToken createRefreshToken(
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    RandomStringGenerator randomStringGenerator = new RandomStringGenerator(24);
    String code = randomStringGenerator.generate();
    RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity(code);

    LocalDateTime localDateTime = SystemDateTime.now();
    CreatedAt createdAt = new CreatedAt(localDateTime);
    long refreshTokenDuration = authorizationServerConfiguration.refreshTokenDuration();
    ExpiredAt expiredAt = new ExpiredAt(localDateTime.plusSeconds(refreshTokenDuration));

    return new RefreshToken(refreshTokenEntity, createdAt, expiredAt);
  }
}
