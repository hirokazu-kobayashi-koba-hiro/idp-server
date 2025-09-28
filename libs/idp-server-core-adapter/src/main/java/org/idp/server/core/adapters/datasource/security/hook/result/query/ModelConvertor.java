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

package org.idp.server.core.adapters.datasource.security.hook.result.query;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.event.*;
import org.idp.server.platform.security.hook.SecurityEventHookResult;
import org.idp.server.platform.security.hook.SecurityEventHookResultIdentifier;
import org.idp.server.platform.security.hook.SecurityEventHookStatus;
import org.idp.server.platform.security.hook.SecurityEventHookType;
import org.idp.server.platform.security.type.IpAddress;
import org.idp.server.platform.security.type.UserAgent;

public class ModelConvertor {

  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static SecurityEventHookResult convert(Map<String, String> result) {

    SecurityEventHookResultIdentifier identifier =
        new SecurityEventHookResultIdentifier(result.get("id"));
    SecurityEventHookStatus status = SecurityEventHookStatus.valueOf(result.get("status"));
    SecurityEventHookType type = new SecurityEventHookType(result.get("security_event_hook"));
    SecurityEvent securityEvent =
        toSecurityEvent(JsonNodeWrapper.fromString(result.get("security_event_payload")));
    Map<String, Object> contents =
        JsonNodeWrapper.fromString(result.get("security_event_hook_execution_payload")).toMap();
    LocalDateTime createdAt = LocalDateTimeParser.parse(result.get("created_at"));
    LocalDateTime updatedAt = LocalDateTimeParser.parse(result.get("updated_at"));

    return new SecurityEventHookResult(
        identifier, status, type, securityEvent, contents, createdAt, updatedAt);
  }

  static SecurityEvent toSecurityEvent(JsonNodeWrapper jsonNode) {

    SecurityEventIdentifier identifier =
        new SecurityEventIdentifier(jsonNode.getValueOrEmptyAsString("id"));
    SecurityEventType type = new SecurityEventType(jsonNode.getValueOrEmptyAsString("type"));
    SecurityEventDescription description =
        new SecurityEventDescription(jsonNode.getValueOrEmptyAsString("description"));
    SecurityEventTenant tenant = toSecurityEventTenant(jsonNode.getNode("tenant"));
    SecurityEventClient client = toSecurityEventClient(jsonNode.getNode("client"));

    SecurityEventUser user = toSecurityEventUser(jsonNode.getNode("user"));
    IpAddress ipAddress = new IpAddress(jsonNode.getValueOrEmptyAsString("ip_address"));
    UserAgent userAgent = new UserAgent(jsonNode.getValueOrEmptyAsString("user_agent"));
    SecurityEventDetail detail = new SecurityEventDetail(jsonNode.getNode("detail").toMap());
    SecurityEventDatetime createdAt =
        new SecurityEventDatetime(
            LocalDateTimeParser.parse(jsonNode.getValueOrEmptyAsString("created_at")));

    return new SecurityEvent(
        identifier,
        type,
        description,
        tenant,
        client,
        user,
        ipAddress,
        userAgent,
        detail,
        createdAt);
  }

  static SecurityEventTenant toSecurityEventTenant(JsonNodeWrapper jsonNode) {
    String id = jsonNode.getValueOrEmptyAsString("id");
    String iss = jsonNode.getValueOrEmptyAsString("iss");
    String name = jsonNode.getValueOrEmptyAsString("name");
    return new SecurityEventTenant(id, iss, name);
  }

  static SecurityEventClient toSecurityEventClient(JsonNodeWrapper jsonNode) {
    String id = jsonNode.getValueOrEmptyAsString("id");
    String name = jsonNode.getValueOrEmptyAsString("name");
    return new SecurityEventClient(id, name);
  }

  static SecurityEventUser toSecurityEventUser(JsonNodeWrapper jsonNode) {
    return jsonConverter.read(jsonNode.toMap(), SecurityEventUser.class);
  }
}
