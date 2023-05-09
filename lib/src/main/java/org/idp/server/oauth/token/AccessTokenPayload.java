package org.idp.server.oauth.token;

import java.util.Map;
import org.idp.server.type.extension.CreatedAt;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.extension.ExpiredAt;
import org.idp.server.type.oauth.*;

public class AccessTokenPayload {
  TokenIssuer tokenIssuer;
  Subject subject;
  ClientId clientId;
  Scopes scopes;
  CustomProperties customProperties;
  CreatedAt createdAt;
  ExpiredAt expiredAt;
  Map<String, Object> values;

  public AccessTokenPayload() {}

  AccessTokenPayload(
      TokenIssuer tokenIssuer,
      Subject subject,
      ClientId clientId,
      Scopes scopes,
      CustomProperties customProperties,
      CreatedAt createdAt,
      ExpiredAt expiredAt,
      Map<String, Object> values) {
    this.tokenIssuer = tokenIssuer;
    this.subject = subject;
    this.clientId = clientId;
    this.scopes = scopes;
    this.customProperties = customProperties;
    this.createdAt = createdAt;
    this.expiredAt = expiredAt;
    this.values = values;
  }

  public TokenIssuer tokenIssuer() {
    return tokenIssuer;
  }

  public Subject subject() {
    return subject;
  }

  public ClientId clientId() {
    return clientId;
  }

  public Scopes scopes() {
    return scopes;
  }

  public CustomProperties customProperties() {
    return customProperties;
  }

  public CreatedAt createdAt() {
    return createdAt;
  }

  public ExpiredAt expiredAt() {
    return expiredAt;
  }

  public Map<String, Object> values() {
    return values;
  }

  public boolean hasOpenidScope() {
    return scopes.contains("openid");
  }
}
