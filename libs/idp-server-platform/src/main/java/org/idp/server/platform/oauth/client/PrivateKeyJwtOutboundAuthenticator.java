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
package org.idp.server.platform.oauth.client;

import java.util.Map;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.exception.InvalidConfigurationException;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.jose.JsonWebKey;
import org.idp.server.platform.jose.JsonWebKeyInvalidException;
import org.idp.server.platform.jose.JsonWebSignatureFactory;
import org.idp.server.platform.jose.JwkParser;
import org.idp.server.platform.json.JsonConverter;

/**
 * {@code private_key_jwt}: a client assertion signed with the client's private key.
 *
 * <p>The key is {@code client_authentication.signing_key}, one JWK holding the private part. The
 * algorithm is {@code client_authentication.signing_algorithm}, or the key's own {@code alg} when
 * that is not set.
 */
public class PrivateKeyJwtOutboundAuthenticator implements OutboundClientAuthenticator {

  JsonWebSignatureFactory signatureFactory = new JsonWebSignatureFactory();
  JsonConverter jsonConverter = JsonConverter.defaultInstance();

  @Override
  public OutboundClientAuthenticationType type() {
    return OutboundClientAuthenticationType.private_key_jwt;
  }

  @Override
  public OutboundClientAuthentication authenticate(OutboundClientCredentials credentials) {
    OutboundClientAuthenticationConfig config = credentials.config();
    if (!config.hasSigningKey()) {
      throw new InvalidConfigurationException(
          "private_key_jwt needs client_authentication.signing_key");
    }
    try {
      JsonWebKey signingKey = JwkParser.parse(jsonConverter.write(config.signingKey()));
      if (!signingKey.isPrivate()) {
        throw new InvalidConfigurationException(
            "client_authentication.signing_key must hold the private key");
      }
      String algorithm =
          config.hasSigningAlgorithm() ? config.signingAlgorithm() : signingKey.algorithm();
      if (algorithm == null || algorithm.isEmpty()) {
        throw new InvalidConfigurationException(
            "private_key_jwt needs client_authentication.signing_algorithm or an alg on the key");
      }
      String assertion =
          signatureFactory
              .createWithAsymmetricKey(
                  ClientAssertionClaims.of(credentials, SystemDateTime.now()).toMap(),
                  Map.of(),
                  signingKey,
                  algorithm)
              .serialize();
      return new OutboundClientAuthentication(
          Map.of(),
          Map.of(
              "client_id",
              credentials.clientId(),
              "client_assertion_type",
              ClientAssertionClaims.ASSERTION_TYPE,
              "client_assertion",
              assertion));
    } catch (JsonWebKeyInvalidException | JoseInvalidException e) {
      throw new InvalidConfigurationException(
          "private_key_jwt could not sign the client assertion: " + e.getMessage(), e);
    }
  }
}
