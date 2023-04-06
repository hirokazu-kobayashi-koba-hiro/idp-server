package org.idp.server.core.token;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.type.*;

public class AccessTokenPayloadBuilder {
  TokenIssuer tokenIssuer;
  Subject subject;
  ClientId clientId;
  Scopes scopes;
  CustomProperties customProperties;
  CreatedAt createdAt;
  ExpiredAt expiredAt;
  Map<String, Object> values = new HashMap<>();

  public AccessTokenPayloadBuilder() {}

  public AccessTokenPayloadBuilder add(TokenIssuer tokenIssuer) {
    this.tokenIssuer = tokenIssuer;
    values.put("iss", tokenIssuer.value());
    return this;
  }

  public AccessTokenPayloadBuilder add(Subject subject) {
    this.subject = subject;
    values.put("sub", subject.value());
    return this;
  }

  public AccessTokenPayloadBuilder add(ClientId clientId) {
    this.clientId = clientId;
    values.put("client_id", clientId.value());
    return this;
  }

  public AccessTokenPayloadBuilder add(Scopes scopes) {
    this.scopes = scopes;
    values.put("scope", scopes.toStringValues());
    return this;
  }

  public AccessTokenPayloadBuilder add(CustomProperties customProperties) {
    if (customProperties.exists()) {
      this.customProperties = customProperties;
      values.putAll(customProperties.values());
    }
    return this;
  }

  public AccessTokenPayloadBuilder add(CreatedAt createdAt) {
    this.createdAt = createdAt;
    values.put("iat", createdAt.toEpochSecondWithUtc());
    return this;
  }

  public AccessTokenPayloadBuilder add(ExpiredAt expiredAt) {
    this.expiredAt = expiredAt;
    values.put("exp", expiredAt.toEpochSecondWithUtc());
    return this;
  }

  public AccessTokenPayload build() {
    return new AccessTokenPayload(
        tokenIssuer, subject, clientId, scopes, customProperties, createdAt, expiredAt, values);
  }
}
