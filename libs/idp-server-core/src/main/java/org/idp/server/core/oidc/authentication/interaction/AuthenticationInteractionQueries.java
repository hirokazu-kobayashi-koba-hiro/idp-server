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

package org.idp.server.core.oidc.authentication.interaction;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.uuid.UuidConvertable;

public class AuthenticationInteractionQueries implements UuidConvertable {
  Map<String, String> values;

  public AuthenticationInteractionQueries() {}

  public AuthenticationInteractionQueries(Map<String, String> values) {
    this.values = Objects.requireNonNullElseGet(values, HashMap::new);
  }

  public LocalDateTime from() {
    if (!values.containsKey("from")) {
      return SystemDateTime.now().minusDays(7);
    }
    return LocalDateTimeParser.parse(values.get("from"));
  }

  public LocalDateTime to() {
    if (!values.containsKey("to")) {
      return SystemDateTime.now().plusDays(1);
    }
    return LocalDateTimeParser.parse(values.get("to"));
  }

  public String type() {
    return values.get("type");
  }

  public boolean hasType() {
    return values.containsKey("type");
  }

  public int limit() {
    if (!values.containsKey("limit")) {
      return 20;
    }
    return Integer.parseInt(values.get("limit"));
  }

  public int offset() {
    if (!values.containsKey("offset")) {
      return 0;
    }
    return Integer.parseInt(values.get("offset"));
  }
}
