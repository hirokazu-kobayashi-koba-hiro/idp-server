package org.idp.server.core.sharedsignal;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.TokenIssuer;

public class Event {

  EventIdentifier identifier;
  EventType type;
  EventDescription description;
  EventServer server;
  EventClient client;
  EventUser user;
  IpAddress ipAddress;
  UserAgent userAgent;
  EventDetail detail;
  EventDatetime createdAt;

  public Event() {}

  public Event(
      EventIdentifier identifier,
      EventType type,
      EventDescription description,
      EventServer server,
      EventClient client,
      EventUser user,
      IpAddress ipAddress,
      UserAgent userAgent,
      EventDetail detail,
      EventDatetime createdAt) {
    this.identifier = identifier;
    this.type = type;
    this.description = description;
    this.server = server;
    this.client = client;
    this.user = user;
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
    this.detail = detail;
    this.createdAt = createdAt;
  }

  public EventIdentifier identifier() {
    return identifier;
  }

  public EventType type() {
    return type;
  }

  public EventDescription description() {
    return description;
  }

  public EventServer server() {
    return server;
  }

  public EventClient client() {
    return client;
  }

  public EventUser user() {
    return user;
  }

  public String userSub() {
    return user.id();
  }

  public IpAddress ipAddress() {
    return ipAddress;
  }

  public UserAgent userAgent() {
    return userAgent;
  }

  public EventDetail detail() {
    return detail;
  }

  public EventDatetime createdAt() {
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
    result.put("server", server().toMap());
    result.put("client", client().toMap());
    if (hasUser()) {
      result.put("user", user().toMap());
    }
    result.put("detail", detail.toMap());
    return result;
  }

  public TokenIssuer tokenIssuer() {
    return server.issuer();
  }

  public String tokenIssuerValue() {
    return server.id();
  }

  public ClientId clientId() {
    return client.clientId();
  }
}
