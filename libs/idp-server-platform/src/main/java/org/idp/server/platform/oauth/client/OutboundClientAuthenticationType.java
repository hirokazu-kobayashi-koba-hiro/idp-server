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

import org.idp.server.platform.exception.InvalidConfigurationException;

/**
 * How this server authenticates itself as a client to another server's token endpoint.
 *
 * <p>The outbound counterpart of the token endpoint's own client authentication. {@code none} sends
 * only {@code client_id}, for a public client.
 */
public enum OutboundClientAuthenticationType {
  client_secret_basic,
  client_secret_post,
  client_secret_jwt,
  private_key_jwt,
  none;

  /**
   * The type named {@code value}, or {@code defaultType} when none is named.
   *
   * <p>A value that names no type is refused rather than taken as the default. A misspelt {@code
   * private_key_jwt} used to go out as {@code client_secret_post}, so the setting looked applied
   * when it was not.
   *
   * @throws InvalidConfigurationException when {@code value} names no supported type
   */
  public static OutboundClientAuthenticationType of(
      String value, OutboundClientAuthenticationType defaultType) {
    if (value == null || value.isEmpty()) {
      return defaultType;
    }
    for (OutboundClientAuthenticationType type : values()) {
      if (type.name().equals(value)) {
        return type;
      }
    }
    throw new InvalidConfigurationException("unsupported client_authentication_type: " + value);
  }

  public boolean usesAssertion() {
    return this == client_secret_jwt || this == private_key_jwt;
  }
}
