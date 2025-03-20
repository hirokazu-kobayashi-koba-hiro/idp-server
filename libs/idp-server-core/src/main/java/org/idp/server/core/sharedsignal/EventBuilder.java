package org.idp.server.core.sharedsignal;

import java.util.UUID;
import org.idp.server.core.basic.date.SystemDateTime;

public class EventBuilder {
  EventIdentifier identifier;
  EventType type;
  EventDescription description;
  EventTenant server;
  EventClient client;
  EventUser user;
  IpAddress ipAddress;
  UserAgent userAgent;
  EventDetail detail;
  EventDatetime createdAt;

  public EventBuilder() {
    this.identifier = new EventIdentifier(UUID.randomUUID().toString());
    this.createdAt = new EventDatetime(SystemDateTime.now());
  }

  public EventBuilder add(EventType type) {
    this.type = type;
    return this;
  }

  public EventBuilder add(EventDescription description) {
    this.description = description;
    return this;
  }

  public EventBuilder add(EventTenant server) {
    this.server = server;
    return this;
  }

  public EventBuilder add(EventClient client) {
    this.client = client;
    return this;
  }

  public EventBuilder add(EventUser user) {
    this.user = user;
    return this;
  }

  public EventBuilder add(IpAddress ipAddress) {
    this.ipAddress = ipAddress;
    return this;
  }

  public EventBuilder add(UserAgent userAgent) {
    this.userAgent = userAgent;
    return this;
  }

  public EventBuilder add(EventDetail detail) {
    this.detail = detail;
    return this;
  }

  public Event build() {
    return new Event(
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
