package org.idp.server.core.sharedsignal;

public class EventSearchCriteria {
  String eventId;
  String eventServerId;
  String clientId;
  String userId;
  EventType eventType;

  public EventSearchCriteria() {}

  public EventSearchCriteria(
      String eventId, String eventServerId, String clientId, String userId, EventType eventType) {
    this.eventId = eventId;
    this.eventServerId = eventServerId;
    this.clientId = clientId;
    this.userId = userId;
    this.eventType = eventType;
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

  public EventType eventType() {
    return eventType;
  }
}
