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

package org.idp.server.core.openid.oauth.configuration;

/** Strategy for determining the access token expiration behavior during refresh. */
public enum AccessTokenStrategy {
  EXTENDS("Extends the access token lifetime on every refresh request."),
  FIXED("Keeps the original expiration fixed regardless of refresh.");

  String description;

  AccessTokenStrategy(String description) {
    this.description = description;
  }

  public boolean isFixed() {
    return this == FIXED;
  }

  public boolean isExtends() {
    return this == EXTENDS;
  }

  public static AccessTokenStrategy of(String value) {

    for (AccessTokenStrategy strategy : AccessTokenStrategy.values()) {
      if (strategy.name().equalsIgnoreCase(value)) {
        return strategy;
      }
    }

    return FIXED;
  }
}
