package org.idp.server.core.token;

import java.time.LocalDateTime;
import org.idp.server.basic.date.UtcDateTime;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.grant.AuthorizationGranted;
import org.idp.server.core.type.CreatedAt;
import org.idp.server.core.type.ExpiredAt;

public interface AccessTokenPayloadCreatable {
  default AccessTokenPayload createAccessTokenPayload(
      AuthorizationGranted authorizationGranted,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    LocalDateTime localDateTime = UtcDateTime.now();
    CreatedAt createdAt = new CreatedAt(localDateTime);
    long accessTokenDuration = serverConfiguration.accessTokenDuration();
    ExpiredAt expiredAt = new ExpiredAt(localDateTime.plusSeconds(accessTokenDuration));
    AccessTokenPayloadBuilder builder = new AccessTokenPayloadBuilder();
    builder.add(serverConfiguration.issuer());
    builder.add(authorizationGranted.subject());
    builder.add(authorizationGranted.clientId());
    builder.add(authorizationGranted.scopes());
    builder.add(authorizationGranted.customProperties());
    builder.add(createdAt);
    builder.add(expiredAt);
    return builder.build();
  }
}
