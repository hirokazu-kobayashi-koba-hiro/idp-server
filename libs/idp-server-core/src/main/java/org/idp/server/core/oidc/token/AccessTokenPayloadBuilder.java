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

package org.idp.server.core.oidc.token;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.type.extension.CreatedAt;
import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.basic.type.extension.ExpiredAt;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.core.oidc.mtls.ClientCertificationThumbprint;
import org.idp.server.core.oidc.rar.AuthorizationDetails;

public class AccessTokenPayloadBuilder {
  Map<String, Object> values = new HashMap<>();

  public AccessTokenPayloadBuilder() {}

  public AccessTokenPayloadBuilder add(TokenIssuer tokenIssuer) {
    values.put("iss", tokenIssuer.value());
    return this;
  }

  public AccessTokenPayloadBuilder add(Subject subject) {
    if (subject.exists()) {
      values.put("sub", subject.value());
    }
    return this;
  }

  public AccessTokenPayloadBuilder add(RequestedClientId requestedClientId) {
    values.put("client_id", requestedClientId.value());
    return this;
  }

  public AccessTokenPayloadBuilder add(Scopes scopes) {
    values.put("scope", scopes.toStringValues());
    return this;
  }

  public AccessTokenPayloadBuilder add(CustomProperties customProperties) {
    if (customProperties.exists()) {
      values.putAll(customProperties.values());
    }
    return this;
  }

  public AccessTokenPayloadBuilder addJti(String jti) {
    values.put("jti", jti);
    return this;
  }

  public AccessTokenPayloadBuilder add(CreatedAt createdAt) {
    values.put("iat", createdAt.toEpochSecondWithUtc());
    return this;
  }

  public AccessTokenPayloadBuilder add(ExpiredAt expiredAt) {
    values.put("exp", expiredAt.toEpochSecondWithUtc());
    return this;
  }

  public AccessTokenPayloadBuilder add(AuthorizationDetails authorizationDetails) {
    if (authorizationDetails.exists()) {
      values.put("authorization_details", authorizationDetails.toMapValues());
    }
    return this;
  }

  public AccessTokenPayloadBuilder add(ClientCertificationThumbprint thumbprint) {
    if (thumbprint.exists()) {
      values.put("cnf", Map.of("x5t#S256", thumbprint.value()));
    }
    return this;
  }

  public Map<String, Object> build() {
    return values;
  }
}
