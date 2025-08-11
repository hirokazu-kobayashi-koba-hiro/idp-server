/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.openid.oauth.response;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.exception.ConfigurationInvalidException;
import org.idp.server.core.openid.oauth.type.extension.CreatedAt;
import org.idp.server.core.openid.oauth.type.extension.ExpiresAt;
import org.idp.server.core.openid.oauth.type.extension.JarmPayload;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.jose.JsonWebKeyInvalidException;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebSignatureFactory;

public interface JarmCreatable {

  default JarmPayload createResponse(
      AuthorizationResponse authorizationResponse,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    try {
      LocalDateTime localDateTime = SystemDateTime.now();
      CreatedAt createdAt = new CreatedAt(localDateTime);
      long authorizationResponseDuration =
          authorizationServerConfiguration.authorizationResponseDuration();
      ExpiresAt expiresAt = new ExpiresAt(localDateTime.plusSeconds(authorizationResponseDuration));

      Map<String, Object> payload = new HashMap<>();
      payload.put("iss", authorizationResponse.tokenIssuer().value());
      payload.put("aud", clientConfiguration.clientIdValue());
      payload.put("iat", createdAt.toEpochSecondWithUtc());
      payload.put("exp", expiresAt.toEpochSecondWithUtc());
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
              authorizationServerConfiguration.jwks(),
              clientConfiguration.authorizationSignedResponseAlg());

      return new JarmPayload(jsonWebSignature.serialize());
    } catch (JoseInvalidException | JsonWebKeyInvalidException exception) {
      throw new ConfigurationInvalidException(exception);
    }
  }

  default JarmPayload createResponse(
      AuthorizationErrorResponse errorResponse,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    try {
      LocalDateTime localDateTime = SystemDateTime.now();
      CreatedAt createdAt = new CreatedAt(localDateTime);
      long authorizationResponseDuration =
          authorizationServerConfiguration.authorizationResponseDuration();
      ExpiresAt expiresAt = new ExpiresAt(localDateTime.plusSeconds(authorizationResponseDuration));

      Map<String, Object> payload = new HashMap<>();
      payload.put("iss", errorResponse.tokenIssuer().value());
      payload.put("aud", clientConfiguration.clientIdValue());
      payload.put("iat", createdAt.toEpochSecondWithUtc());
      payload.put("exp", expiresAt.toEpochSecondWithUtc());
      payload.put("error", errorResponse.error().value());
      payload.put("error_description", errorResponse.errorDescription().value());
      JsonWebSignatureFactory jsonWebSignatureFactory = new JsonWebSignatureFactory();
      JsonWebSignature jsonWebSignature =
          jsonWebSignatureFactory.createWithAsymmetricKeyByAlgorithm(
              payload,
              Map.of(),
              authorizationServerConfiguration.jwks(),
              clientConfiguration.authorizationSignedResponseAlg());

      return new JarmPayload(jsonWebSignature.serialize());
    } catch (JoseInvalidException | JsonWebKeyInvalidException exception) {
      throw new ConfigurationInvalidException(exception);
    }
  }
}
