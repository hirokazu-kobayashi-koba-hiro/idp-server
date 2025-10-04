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

package org.idp.server.core.adapters.datasource.security.event.query;

import java.util.Map;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.event.*;
import org.idp.server.platform.security.type.IpAddress;
import org.idp.server.platform.security.type.UserAgent;

public class ModelConvertor {

  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static SecurityEvent convert(Map<String, String> result) {
    SecurityEventIdentifier identifier = new SecurityEventIdentifier(result.get("id"));
    SecurityEventType type = new SecurityEventType(result.get("type"));
    SecurityEventDescription description = new SecurityEventDescription(result.get("description"));
    SecurityEventTenant tenant =
        new SecurityEventTenant(result.get("tenant_id"), "", result.get("tenant_name"));
    SecurityEventClient client =
        new SecurityEventClient(result.get("client_id"), result.get("client_name"));

    Map<String, Object> detailMap = JsonNodeWrapper.fromString(result.get("detail")).toMap();
    SecurityEventUser user =
        extractUserFromDetail(
            result.get("user_id"),
            result.get("user_name"),
            result.get("external_user_id"),
            detailMap);
    IpAddress ipAddress = new IpAddress(result.get("ip_address"));
    UserAgent userAgent = new UserAgent(result.get("user_agent"));
    SecurityEventDetail detail = new SecurityEventDetail(detailMap);
    SecurityEventDatetime createdAt =
        new SecurityEventDatetime(LocalDateTimeParser.parse(result.get("created_at")));

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

  private static SecurityEventUser extractUserFromDetail(
      String userId, String userName, String externalUserId, Map<String, Object> detailMap) {

    if (detailMap.containsKey("user")) {
      JsonNodeWrapper userNode = JsonNodeWrapper.fromMap(detailMap).getNode("user");
      return jsonConverter.read(userNode.toMap(), SecurityEventUser.class);
    }

    // Backward compatibility: for old data without detail.user
    return new SecurityEventUser(userId, userName, externalUserId, "", "");
  }
}
