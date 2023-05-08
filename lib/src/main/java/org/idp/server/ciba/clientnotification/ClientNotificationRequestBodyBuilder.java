package org.idp.server.ciba.clientnotification;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonParser;
import org.idp.server.type.ciba.AuthReqId;
import org.idp.server.type.oauth.AccessTokenValue;
import org.idp.server.type.oauth.ExpiresIn;
import org.idp.server.type.oauth.RefreshTokenValue;
import org.idp.server.type.oauth.TokenType;
import org.idp.server.type.oidc.IdToken;

public class ClientNotificationRequestBodyBuilder {

  Map<String, Object> values;
  JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();

  public ClientNotificationRequestBodyBuilder() {
    this.values = new HashMap<>();
  }

  public ClientNotificationRequestBodyBuilder add(AuthReqId authReqId) {
    values.put("auth_req_id", authReqId.value());
    return this;
  }

  public ClientNotificationRequestBodyBuilder add(AccessTokenValue accessTokenValue) {
    values.put("access_token", accessTokenValue.value());
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

  public ClientNotificationRequestBodyBuilder add(RefreshTokenValue refreshTokenValue) {
    values.put("refresh_token", refreshTokenValue.value());
    return this;
  }

  public ClientNotificationRequestBodyBuilder add(IdToken idToken) {
    values.put("id_token", idToken.value());
    return this;
  }

  public String build() {
    return jsonParser.write(values);
  }
}
