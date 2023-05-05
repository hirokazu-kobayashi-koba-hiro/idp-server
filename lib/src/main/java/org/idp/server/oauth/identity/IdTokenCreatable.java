package org.idp.server.oauth.identity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.basic.jose.JsonWebSignature;
import org.idp.server.basic.jose.JsonWebSignatureFactory;
import org.idp.server.basic.jose.JwkInvalidException;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.IdToken;

public interface IdTokenCreatable extends IndividualClaimsCreatable, ClaimHashable {

  default IdToken createIdToken(
      User user,
      Authentication authentication,
      Scopes scopes,
      IdTokenClaims idTokenClaims,
      IdTokenCustomClaims customClaims,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    try {
      Map<String, Object> claims =
          createClaims(
              user,
              authentication,
              customClaims,
              scopes,
              idTokenClaims,
              serverConfiguration.tokenIssuer(),
              clientConfiguration.clientId(),
              3600);
      JsonWebSignatureFactory jsonWebSignatureFactory = new JsonWebSignatureFactory();
      JsonWebSignature jsonWebSignature =
          jsonWebSignatureFactory.createWithAsymmetricKey(
              claims, Map.of(), serverConfiguration.jwks(), serverConfiguration.tokenSignedKeyId());
      return new IdToken(jsonWebSignature.serialize());
    } catch (JwkInvalidException e) {
      throw new RuntimeException(e);
    }
  }

  // FIXME user claim
  private Map<String, Object> createClaims(
      User user,
      Authentication authentication,
      IdTokenCustomClaims idTokenCustomClaims,
      Scopes scopes,
      IdTokenClaims idTokenClaims,
      TokenIssuer tokenIssuer,
      ClientId clientId,
      int idTokenDuration) {
    LocalDateTime now = SystemDateTime.now();
    ExpiredAt expiredAt = new ExpiredAt(now.plusSeconds(idTokenDuration));
    HashMap<String, Object> claims = new HashMap<>();
    claims.put("iss", tokenIssuer.value());
    claims.put("aud", clientId.value());
    claims.put("exp", expiredAt.toEpochSecondWithUtc());
    claims.put("iat", now.toEpochSecond(SystemDateTime.zoneOffset));
    if (idTokenCustomClaims.hasNonce()) {
      claims.put("nonce", idTokenCustomClaims.nonce().value());
    }
    if (idTokenCustomClaims.hasState()) {
      State state = idTokenCustomClaims.state();
      claims.put("s_hash", hash(state.value(), "ES256"));
    }
    if (idTokenCustomClaims.hasAuthorizationCode()) {
      claims.put("c_hash", hash(idTokenCustomClaims.authorizationCode().value(), "ES256"));
    }
    if (idTokenCustomClaims.hasAccessTokenValue()) {
      claims.put("at_hash", hash(idTokenCustomClaims.accessTokenValue().value(), "ES256"));
    }

    IdTokenIndividualClaimsDecider idTokenIndividualClaimsDecider =
        new IdTokenIndividualClaimsDecider(scopes, idTokenClaims, false);
    Map<String, Object> individualClaims =
        createIndividualClaims(user, idTokenIndividualClaimsDecider);
    claims.putAll(individualClaims);
    return claims;
  }
}
