package org.idp.server.oauth.identity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.date.UtcDateTime;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.type.oauth.AccessToken;
import org.idp.server.type.oauth.AuthorizationCode;
import org.idp.server.type.oauth.ExpiredAt;
import org.idp.server.type.oidc.IdToken;

public interface IdTokenCreatable extends ClaimHashable {

  default Map<String, Object> createClaims(
      AuthorizationRequest authorizationRequest,
      AuthorizationCode authorizationCode,
      AccessToken accessToken,
      User user,
      int idTokenDuration) {
    LocalDateTime now = UtcDateTime.now();
    ExpiredAt expiredAt = new ExpiredAt(now.plusSeconds(idTokenDuration));
    HashMap<String, Object> claims = new HashMap<>();
    claims.put("exp", expiredAt.toEpochSecondWithUtc());
    claims.put("iat", UtcDateTime.toEpochSecond(now));
    return claims;
  }

  default IdToken createIdToken() {
    return new IdToken();
  }
}
