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

package org.idp.server.core.extension.ciba.clientnotification;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.oauth.type.ciba.AuthReqId;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.oauth.type.oauth.ExpiresIn;
import org.idp.server.core.openid.oauth.type.oauth.RefreshTokenEntity;
import org.idp.server.core.openid.oauth.type.oauth.TokenType;
import org.idp.server.core.openid.oauth.type.oidc.IdToken;
import org.idp.server.platform.json.JsonConverter;

public class ClientNotificationRequestBodyBuilder {

  Map<String, Object> values;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public ClientNotificationRequestBodyBuilder() {
    this.values = new HashMap<>();
  }

  public ClientNotificationRequestBodyBuilder add(AuthReqId authReqId) {
    values.put("auth_req_id", authReqId.value());
    return this;
  }

  public ClientNotificationRequestBodyBuilder add(AccessTokenEntity accessTokenEntity) {
    values.put("access_token", accessTokenEntity.value());
    return this;
  }

  public ClientNotificationRequestBodyBuilder add(TokenType tokenType) {
    values.put("token_type", tokenType.name());
    return this;
  }

  public ClientNotificationRequestBodyBuilder add(ExpiresIn expiresIn) {
    values.put("expires_in", expiresIn.value());
    return this;
  }

  public ClientNotificationRequestBodyBuilder add(RefreshTokenEntity refreshTokenEntity) {
    values.put("refresh_token", refreshTokenEntity.value());
    return this;
  }

  public ClientNotificationRequestBodyBuilder add(IdToken idToken) {
    values.put("id_token", idToken.value());
    return this;
  }

  public String build() {
    return jsonConverter.write(values);
  }
}
