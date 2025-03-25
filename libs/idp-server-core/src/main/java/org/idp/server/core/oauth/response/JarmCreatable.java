package org.idp.server.core.oauth.response;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.basic.jose.JoseInvalidException;
import org.idp.server.core.basic.jose.JsonWebKeyInvalidException;
import org.idp.server.core.basic.jose.JsonWebSignature;
import org.idp.server.core.basic.jose.JsonWebSignatureFactory;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ConfigurationInvalidException;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.type.extension.CreatedAt;
import org.idp.server.core.type.extension.ExpiredAt;
import org.idp.server.core.type.extension.JarmPayload;

public interface JarmCreatable {

  default JarmPayload createResponse(
      AuthorizationResponse authorizationResponse,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    try {
      LocalDateTime localDateTime = SystemDateTime.now();
      CreatedAt createdAt = new CreatedAt(localDateTime);
      long authorizationResponseDuration = serverConfiguration.authorizationResponseDuration();
      ExpiredAt expiredAt = new ExpiredAt(localDateTime.plusSeconds(authorizationResponseDuration));

      Map<String, Object> payload = new HashMap<>();
      payload.put("iss", authorizationResponse.tokenIssuer().value());
      payload.put("aud", clientConfiguration.clientIdValue());
      payload.put("iat", createdAt.toEpochSecondWithUtc());
      payload.put("exp", expiredAt.toEpochSecondWithUtc());
      if (authorizationResponse.hasState()) {
        payload.put("state", authorizationResponse.state().value());
      }
      if (authorizationResponse.hasAuthorizationCode()) {
        payload.put("code", authorizationResponse.authorizationCode().value());
      }
      if (authorizationResponse.hasAccessToken()) {
        payload.put(
            "access_token", authorizationResponse.accessToken().accessTokenEntity().value());
        payload.put("token_type", authorizationResponse.tokenType().name());
        payload.put("expires_in", authorizationResponse.expiresIn().toStringValue());
        payload.put("scope", authorizationResponse.scopes().toStringValues());
      }
      if (authorizationResponse.hasIdToken()) {
        payload.put("id_token", authorizationResponse.idToken().value());
      }
      JsonWebSignatureFactory jsonWebSignatureFactory = new JsonWebSignatureFactory();
      JsonWebSignature jsonWebSignature =
          jsonWebSignatureFactory.createWithAsymmetricKeyByAlgorithm(
              payload,
              Map.of(),
              serverConfiguration.jwks(),
              clientConfiguration.authorizationSignedResponseAlg());

      return new JarmPayload(jsonWebSignature.serialize());
    } catch (JoseInvalidException | JsonWebKeyInvalidException exception) {
      throw new ConfigurationInvalidException(exception);
    }
  }

  default JarmPayload createResponse(
      AuthorizationErrorResponse errorResponse,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    try {
      LocalDateTime localDateTime = SystemDateTime.now();
      CreatedAt createdAt = new CreatedAt(localDateTime);
      long authorizationResponseDuration = serverConfiguration.authorizationResponseDuration();
      ExpiredAt expiredAt = new ExpiredAt(localDateTime.plusSeconds(authorizationResponseDuration));

      Map<String, Object> payload = new HashMap<>();
      payload.put("iss", errorResponse.tokenIssuer().value());
      payload.put("aud", clientConfiguration.clientIdValue());
      payload.put("iat", createdAt.toEpochSecondWithUtc());
      payload.put("exp", expiredAt.toEpochSecondWithUtc());
      payload.put("error", errorResponse.error().value());
      payload.put("error_description", errorResponse.errorDescription().value());
      JsonWebSignatureFactory jsonWebSignatureFactory = new JsonWebSignatureFactory();
      JsonWebSignature jsonWebSignature =
          jsonWebSignatureFactory.createWithAsymmetricKeyByAlgorithm(
              payload,
              Map.of(),
              serverConfiguration.jwks(),
              clientConfiguration.authorizationSignedResponseAlg());

      return new JarmPayload(jsonWebSignature.serialize());
    } catch (JoseInvalidException | JsonWebKeyInvalidException exception) {
      throw new ConfigurationInvalidException(exception);
    }
  }
}
