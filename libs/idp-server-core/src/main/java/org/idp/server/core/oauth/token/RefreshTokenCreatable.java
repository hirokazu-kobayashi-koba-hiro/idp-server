package org.idp.server.core.oauth.token;

import java.time.LocalDateTime;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.basic.random.RandomStringGenerator;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.basic.type.extension.CreatedAt;
import org.idp.server.basic.type.extension.ExpiredAt;
import org.idp.server.basic.type.oauth.RefreshTokenEntity;

public interface RefreshTokenCreatable {

  default RefreshToken createRefreshToken(
      ServerConfiguration serverConfiguration, ClientConfiguration clientConfiguration) {
    RandomStringGenerator randomStringGenerator = new RandomStringGenerator(24);
    String code = randomStringGenerator.generate();
    RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity(code);

    LocalDateTime localDateTime = SystemDateTime.now();
    CreatedAt createdAt = new CreatedAt(localDateTime);
    long refreshTokenDuration = serverConfiguration.refreshTokenDuration();
    ExpiredAt expiredAt = new ExpiredAt(localDateTime.plusSeconds(refreshTokenDuration));

    return new RefreshToken(refreshTokenEntity, createdAt, expiredAt);
  }
}
