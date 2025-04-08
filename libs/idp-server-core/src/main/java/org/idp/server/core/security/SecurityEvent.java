package org.idp.server.core.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.security.event.*;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.oauth.RequestedClientId;
import org.idp.server.core.type.oauth.TokenIssuer;
import org.idp.server.core.type.security.IpAddress;
import org.idp.server.core.type.security.UserAgent;

public class SecurityEvent {

  SecurityEventIdentifier identifier;
  SecurityEventType type;
  SecurityEventDescription description;
  SecurityEventTenant tenant;
  SecurityEventClient client;
  SecurityEventUser user;
  IpAddress ipAddress;
  UserAgent userAgent;
  SecurityEventDetail detail;
  SecurityEventDatetime createdAt;

  public SecurityEvent() {}

  public SecurityEvent(
      SecurityEventIdentifier identifier,
      SecurityEventType type,
      SecurityEventDescription description,
      SecurityEventTenant tenant,
      SecurityEventClient client,
      SecurityEventUser user,
      IpAddress ipAddress,
      UserAgent userAgent,
      SecurityEventDetail detail,
      SecurityEventDatetime createdAt) {
    this.identifier = identifier;
    this.type = type;
    this.description = description;
    this.tenant = tenant;
    this.client = client;
    this.user = user;
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
    this.detail = detail;
    this.createdAt = createdAt;
  }

  public SecurityEventIdentifier identifier() {
    return identifier;
  }

  public SecurityEventType type() {
    return type;
  }

  public SecurityEventDescription description() {
    return description;
  }

  public SecurityEventTenant tenant() {
    return tenant;
  }

  public SecurityEventClient client() {
    return client;
  }

  public SecurityEventUser user() {
    return user;
  }

  public String userSub() {
    return user.id();
  }

  public IpAddress ipAddress() {
    return ipAddress;
  }

  public String ipAddressValue() {
    if (ipAddress == null) {
      return null;
    }
    return ipAddress.value();
  }

  public UserAgent userAgent() {
    return userAgent;
  }

  public String userAgentValue() {
    if (userAgent == null) {
      return null;
    }
    return userAgent.value();
  }

  public SecurityEventDetail detail() {
    return detail;
  }

  public SecurityEventDatetime createdAt() {
    return createdAt;
  }

  public boolean hasUser() {
    return Objects.nonNull(user) && user.exists();
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> result = new HashMap<>();
    result.put("id", identifier.value());
    result.put("type", type.value());
    result.put("description", description.value());
    result.put("tenant", tenant().toMap());
    result.put("client", client().toMap());
    if (hasUser()) {
      result.put("user", user().toMap());
    }
    result.put("detail", detail.toMap());
    return result;
  }

  public TenantIdentifier tenantIdentifier() {
    return new TenantIdentifier(tenant.id());
  }

  public String tenantIdentifierValue() {
    return tenant.id();
  }

  public String tokenIssuerValue() {
    return tenant.issuerAsString();
  }

  public RequestedClientId clientId() {
    return client.clientId();
  }

  public TokenIssuer tokenIssuer() {
    return tenant.issuer();
  }
}
