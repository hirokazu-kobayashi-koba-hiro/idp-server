package org.idp.server.ciba.clientnotification;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.type.ciba.AuthReqId;
import org.idp.server.type.oauth.AccessTokenEntity;
import org.idp.server.type.oauth.ExpiresIn;
import org.idp.server.type.oauth.RefreshTokenEntity;
import org.idp.server.type.oauth.TokenType;
import org.idp.server.type.oidc.IdToken;

public class ClientNotificationRequestBodyBuilder {

  Map<String, Object> values;
  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

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
