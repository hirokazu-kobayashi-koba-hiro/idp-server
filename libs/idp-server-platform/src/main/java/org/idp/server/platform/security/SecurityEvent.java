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

package org.idp.server.platform.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.event.*;
import org.idp.server.platform.security.type.IpAddress;
import org.idp.server.platform.security.type.UserAgent;

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
    if (user == null) {
      return "";
    }
    return user.sub();
  }

  public String userExSub() {
    if (user == null) {
      return null;
    }
    return user.exSub();
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
    result.put("ip_address", ipAddress.value());
    result.put("user_agent", userAgent.value());
    result.put("detail", detail.toMap());
    result.put("created_at", createdAt.valueAsString());
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

  public String clientId() {
    return client.clientId();
  }

  public String clientIdentifierValue() {
    return client.id();
  }

  public String tokenIssuer() {
    return tenant.issuer();
  }

  public boolean exists() {
    return identifier != null && identifier.exists();
  }
}
