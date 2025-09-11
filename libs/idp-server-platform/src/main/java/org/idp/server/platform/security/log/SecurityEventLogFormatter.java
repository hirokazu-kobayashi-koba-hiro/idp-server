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

public interface SecurityEventLogFormatter {
  String format(SecurityEvent securityEvent, SecurityEventLogConfiguration config);

  String formatWithStage(
      SecurityEvent securityEvent, String stage, SecurityEventLogConfiguration config);

  String formatWithStage(
      SecurityEvent securityEvent,
      String stage,
      SecurityEventLogConfiguration config,
      Map<String, Object> additionalFields);

  enum Format {
    STRUCTURED_JSON("structured_json", "JSON format for log aggregation platforms"),
    KEY_VALUE("key_value", "Key-value format for search-optimized platforms"),
    SIMPLE("simple", "Simple text format"),
    OBSERVABILITY("observability", "Enhanced format for observability platforms"),
    DISABLED("disabled", "Disable security event logging");

    private final String value;
    private final String description;

    Format(String value, String description) {
      this.value = value;
      this.description = description;
    }

    public String value() {
      return value;
    }

    public String description() {
      return description;
    }

    public static Format fromValue(String value) {
      for (Format format : values()) {
        if (format.value.equals(value)) {
          return format;
        }
      }
      return STRUCTURED_JSON;
    }
  }
}
