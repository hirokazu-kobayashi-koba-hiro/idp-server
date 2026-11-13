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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * {@code client_secret_basic}: the client's id and secret in the {@code Authorization} header.
 *
 * <p>{@code client_id} stays in the form too, as it always has: the token endpoints this server
 * talks to accept it, and some look the client up by it.
 */
public class ClientSecretBasicOutboundAuthenticator implements OutboundClientAuthenticator {

  @Override
  public OutboundClientAuthenticationType type() {
    return OutboundClientAuthenticationType.client_secret_basic;
  }

  @Override
  public OutboundClientAuthentication authenticate(OutboundClientCredentials credentials) {
    String pair = credentials.clientId() + ":" + credentials.clientSecret();
    String value =
        "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
    return new OutboundClientAuthentication(
        Map.of("Authorization", value), Map.of("client_id", credentials.clientId()));
  }
}
