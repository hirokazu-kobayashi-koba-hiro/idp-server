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
import org.idp.server.platform.jose.JsonWebSignatureFactory;

/**
 * {@code client_secret_jwt}: a client assertion signed with the client secret.
 *
 * <p>HS256 unless {@code client_authentication.signing_algorithm} says otherwise. HS256 needs a
 * secret of 32 bytes or more; a shorter one is refused when signing.
 */
public class ClientSecretJwtOutboundAuthenticator implements OutboundClientAuthenticator {

  static final String DEFAULT_ALGORITHM = "HS256";

  JsonWebSignatureFactory signatureFactory = new JsonWebSignatureFactory();

  @Override
  public OutboundClientAuthenticationType type() {
    return OutboundClientAuthenticationType.client_secret_jwt;
  }

  @Override
  public OutboundClientAuthentication authenticate(OutboundClientCredentials credentials) {
    OutboundClientAuthenticationConfig config = credentials.config();
    String algorithm = config.hasSigningAlgorithm() ? config.signingAlgorithm() : DEFAULT_ALGORITHM;
    try {
      String assertion =
          signatureFactory
              .createWithSymmetricKey(
                  ClientAssertionClaims.of(credentials, SystemDateTime.now()).toMap(),
                  Map.of(),
                  algorithm,
                  credentials.clientSecret())
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
    } catch (JoseInvalidException e) {
      throw new InvalidConfigurationException(
          "client_secret_jwt could not sign the client assertion: " + e.getMessage(), e);
    }
  }
}
