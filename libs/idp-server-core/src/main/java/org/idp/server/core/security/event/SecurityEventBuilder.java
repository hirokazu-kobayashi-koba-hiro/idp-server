package org.idp.server.core.security.event;

import java.util.UUID;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.basic.type.security.IpAddress;
import org.idp.server.basic.type.security.UserAgent;

public class SecurityEventBuilder {
  SecurityEventIdentifier identifier;
  SecurityEventType type;
  SecurityEventDescription description;
  SecurityEventTenant server;
  SecurityEventClient client;
  SecurityEventUser user;
  IpAddress ipAddress;
  UserAgent userAgent;
  SecurityEventDetail detail;
  SecurityEventDatetime createdAt;

  public SecurityEventBuilder() {
    this.identifier = new SecurityEventIdentifier(UUID.randomUUID().toString());
    this.createdAt = new SecurityEventDatetime(SystemDateTime.now());
  }

  public SecurityEventBuilder add(SecurityEventType type) {
    this.type = type;
    return this;
  }

  public SecurityEventBuilder add(SecurityEventDescription description) {
    this.description = description;
    return this;
  }

  public SecurityEventBuilder add(SecurityEventTenant server) {
    this.server = server;
    return this;
  }

  public SecurityEventBuilder add(SecurityEventClient client) {
    this.client = client;
    return this;
  }

  public SecurityEventBuilder add(SecurityEventUser user) {
    this.user = user;
    return this;
  }

  public SecurityEventBuilder add(IpAddress ipAddress) {
    this.ipAddress = ipAddress;
    return this;
  }

  public SecurityEventBuilder add(UserAgent userAgent) {
    this.userAgent = userAgent;
    return this;
  }

  public SecurityEventBuilder add(SecurityEventDetail detail) {
    this.detail = detail;
    return this;
  }

  public SecurityEvent build() {
    return new SecurityEvent(
        identifier,
        type,
        description,
        server,
        client,
        user,
        ipAddress,
        userAgent,
        detail,
        createdAt);
  }
}
