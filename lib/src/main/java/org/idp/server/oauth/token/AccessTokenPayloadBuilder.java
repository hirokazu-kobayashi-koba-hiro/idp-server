package org.idp.server.oauth.token;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.type.extension.CreatedAt;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.extension.ExpiredAt;
import org.idp.server.type.oauth.*;

public class AccessTokenPayloadBuilder {
  Map<String, Object> values = new HashMap<>();

  public AccessTokenPayloadBuilder() {}

  public AccessTokenPayloadBuilder add(TokenIssuer tokenIssuer) {
    values.put("iss", tokenIssuer.value());
    return this;
  }

  public AccessTokenPayloadBuilder add(Subject subject) {
    values.put("sub", subject.value());
    return this;
  }

  public AccessTokenPayloadBuilder add(ClientId clientId) {
    values.put("client_id", clientId.value());
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

  public AccessTokenPayloadBuilder add(CreatedAt createdAt) {
    values.put("iat", createdAt.toEpochSecondWithUtc());
    return this;
  }

  public AccessTokenPayloadBuilder add(ExpiredAt expiredAt) {
    values.put("exp", expiredAt.toEpochSecondWithUtc());
    return this;
  }

  public Map<String, Object> build() {
    return values;
  }
}
