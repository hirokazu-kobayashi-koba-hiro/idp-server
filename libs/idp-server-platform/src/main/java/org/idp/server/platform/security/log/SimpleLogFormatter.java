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

package org.idp.server.platform.security.log;

import java.util.Map;
import org.idp.server.platform.security.SecurityEvent;

public class SimpleLogFormatter implements SecurityEventLogFormatter {

  @Override
  public String format(SecurityEvent securityEvent, SecurityEventLogConfiguration config) {
    return formatWithStage(securityEvent, null, config);
  }

  @Override
  public String formatWithStage(
      SecurityEvent securityEvent, String stage, SecurityEventLogConfiguration config) {
    return formatWithStage(securityEvent, stage, config, Map.of());
  }

  @Override
  public String formatWithStage(
      SecurityEvent securityEvent,
      String stage,
      SecurityEventLogConfiguration config,
      Map<String, Object> additionalFields) {
    StringBuilder sb = new StringBuilder();

    if (stage != null) {
      sb.append("security_event_").append(stage).append(": ");
    } else {
      sb.append("security_event: ");
    }

    sb.append(securityEvent.type().value());

    sb.append(" event_id=").append(securityEvent.identifier().value());

    if (config.includeUserId() && securityEvent.hasUser()) {
      sb.append(" user_id=").append(securityEvent.userSub());
    }

    if (config.includeUserExSub() && securityEvent.hasUser() && securityEvent.userExSub() != null) {
      sb.append(" user_ex_sub=").append(securityEvent.userExSub());
    }

    if (config.includeClientId()) {
      sb.append(" client_id=").append(securityEvent.clientIdentifierValue());
    }

    sb.append(" tenant=").append(securityEvent.tenantIdentifierValue());

    if (config.includeIpAddress() && securityEvent.ipAddressValue() != null) {
      sb.append(" ip=").append(securityEvent.ipAddressValue());
    }

    if (config.includeUserAgent() && securityEvent.userAgentValue() != null) {
      sb.append(" user_agenet=").append(securityEvent.userAgentValue());
    }

    additionalFields.forEach((key, value) -> sb.append(" ").append(key).append("=").append(value));

    return sb.toString();
  }
}
