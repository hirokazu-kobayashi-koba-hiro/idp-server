/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.security.event;

public class SecurityEventSearchCriteria {
  String eventId;
  String eventServerId;
  String clientId;
  String userId;
  SecurityEventType securityEventType;

  public SecurityEventSearchCriteria() {}

  public SecurityEventSearchCriteria(
      String eventId,
      String eventServerId,
      String clientId,
      String userId,
      SecurityEventType securityEventType) {
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
