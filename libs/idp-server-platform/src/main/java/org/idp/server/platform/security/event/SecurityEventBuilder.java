/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.idp.server.platform.security.event;

import java.util.UUID;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.security.type.IpAddress;
import org.idp.server.platform.security.type.UserAgent;

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
