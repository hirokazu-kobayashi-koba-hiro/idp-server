package org.idp.server.oauth.identity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.basic.jose.*;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ConfigurationInvalidException;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.type.extension.ExpiredAt;
import org.idp.server.type.extension.GrantFlow;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.IdToken;

public interface IdTokenCreatable extends IndividualClaimsCreatable, ClaimHashable {

  default IdToken createIdToken(
      User user,
      Authentication authentication,
      GrantFlow grantFlow,
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
              grantFlow,
              customClaims,
              scopes,
              idTokenClaims,
              serverConfiguration.tokenIssuer(),
              clientConfiguration.clientId(),
              serverConfiguration.idTokenDuration(),
              serverConfiguration.claimsSupported(),
              serverConfiguration.idTokenStrictMode());

      JsonWebSignatureFactory jsonWebSignatureFactory = new JsonWebSignatureFactory();
      JsonWebSignature jsonWebSignature =
          jsonWebSignatureFactory.createWithAsymmetricKey(
              claims, Map.of(), serverConfiguration.jwks(), serverConfiguration.tokenSignedKeyId());

      if (clientConfiguration.hasEncryptedIdTokenMeta()) {
        NestedJsonWebEncryptionCreator nestedJsonWebEncryptionCreator =
            new NestedJsonWebEncryptionCreator(
                jsonWebSignature,
                clientConfiguration.idTokenEncryptedResponseAlg(),
                clientConfiguration.idTokenEncryptedResponseEnc(),
                clientConfiguration.jwks());
        String jwe = nestedJsonWebEncryptionCreator.create();
        return new IdToken(jwe);
      }

      return new IdToken(jsonWebSignature.serialize());
    } catch (JoseInvalidException | JsonWebKeyInvalidException exception) {
      throw new ConfigurationInvalidException(exception);
    }
  }

  private Map<String, Object> createClaims(
      User user,
      Authentication authentication,
      GrantFlow grantFlow,
      IdTokenCustomClaims idTokenCustomClaims,
      Scopes scopes,
      IdTokenClaims idTokenClaims,
      TokenIssuer tokenIssuer,
      ClientId clientId,
      long idTokenDuration,
      List<String> supportedClaims,
      boolean enableStrictMode) {

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
    if (authentication.hasAuthenticationTime()) {
      claims.put("auth_time", authentication.time().toEpochSecond(SystemDateTime.zoneOffset));
    }
    if (authentication.hasMethod()) {
      claims.put("amr", authentication.methods());
    }
    if (authentication.hasAcrValues()) {
      claims.put("acr", authentication.toAcr());
    }

    IdTokenIndividualClaimsDecider idTokenIndividualClaimsDecider =
        new IdTokenIndividualClaimsDecider(
            grantFlow, scopes, idTokenClaims, supportedClaims, enableStrictMode);
    Map<String, Object> individualClaims =
        createIndividualClaims(user, idTokenIndividualClaimsDecider);
    claims.putAll(individualClaims);
    return claims;
  }
}
