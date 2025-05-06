package org.idp.server.core.security.event;

public class SecurityEventSearchCriteria {
  String eventId;
  String eventServerId;
  String clientId;
  String userId;
  SecurityEventType securityEventType;

  public SecurityEventSearchCriteria() {}

  public SecurityEventSearchCriteria(String eventId, String eventServerId, String clientId, String userId, SecurityEventType securityEventType) {
    this.eventId = eventId;
    this.eventServerId = eventServerId;
    this.clientId = clientId;
    this.userId = userId;
    this.securityEventType = securityEventType;
  }

  public String eventId() {
    return eventId;
  }

  public String eventServerId() {
    return eventServerId;
  }

  public String clientId() {
    return clientId;
  }

  public String userId() {
    return userId;
  }

  public SecurityEventType eventType() {
    return securityEventType;
  }
}
