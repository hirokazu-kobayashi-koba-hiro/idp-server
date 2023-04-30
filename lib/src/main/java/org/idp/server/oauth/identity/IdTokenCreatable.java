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
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.type.oauth.AccessTokenValue;
import org.idp.server.type.oauth.AuthorizationCode;
import org.idp.server.type.oauth.ExpiredAt;
import org.idp.server.type.oauth.State;
import org.idp.server.type.oidc.IdToken;

public interface IdTokenCreatable extends IndividualClaimsCreatable, ClaimHashable {

  // FIXME user claim
  default Map<String, Object> createClaims(
      AuthorizationRequest authorizationRequest,
      AuthorizationCode authorizationCode,
      AccessTokenValue accessTokenValue,
      User user,
      Authentication authentication,
      int idTokenDuration) {
    LocalDateTime now = SystemDateTime.now();
    ExpiredAt expiredAt = new ExpiredAt(now.plusSeconds(idTokenDuration));
    HashMap<String, Object> claims = new HashMap<>();
    claims.put("iss", authorizationRequest.tokenIssuer().value());
    claims.put("aud", authorizationRequest.clientId().value());
    claims.put("exp", expiredAt.toEpochSecondWithUtc());
    claims.put("iat", now.toEpochSecond(SystemDateTime.zoneOffset));
    if (authorizationRequest.hasNonce()) {
      claims.put("nonce", authorizationRequest.nonce().value());
    }
    if (authorizationRequest.hasState()) {
      State state = authorizationRequest.state();
      claims.put("s_hash", hash(state.value(), "ES256"));
    }
    if (authorizationCode.exists()) {
      claims.put("c_hash", hash(authorizationCode.value(), "ES256"));
    }
    if (accessTokenValue.exists()) {
      claims.put("at_hash", hash(accessTokenValue.value(), "ES256"));
    }
    // FIXME IdTokenClaims
    IdTokenIndividualClaimsDecider idTokenIndividualClaimsDecider =
        new IdTokenIndividualClaimsDecider(
            authorizationRequest.scope(), new IdTokenClaims(), false);
    Map<String, Object> individualClaims =
        createIndividualClaims(user, idTokenIndividualClaimsDecider);
    claims.putAll(individualClaims);
    return claims;
  }

  default IdToken createIdToken(
      AuthorizationRequest authorizationRequest,
      AuthorizationCode authorizationCode,
      AccessTokenValue accessTokenValue,
      User user,
      Authentication authentication,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    try {
      Map<String, Object> claims =
          createClaims(
              authorizationRequest,
              authorizationCode,
              accessTokenValue,
              user,
              authentication,
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
}
