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

package org.idp.server.core.openid.identity.contact.execution;

import java.util.HashMap;
import java.util.Map;

/**
 * The material one contact execution step is given, exposed to mapping rules as {@code
 * $.request_body} (Issue #1416).
 *
 * <p>Mirrors {@code AuthenticationExecutionRequest}. On the login path the map is the body the
 * caller sent; here there is no caller body — the flow is driven by an already-authenticated user —
 * so it is assembled from the operation, under the field names that channel's {@code
 * interactions.{key}.request.schema} already declares ({@code email} / {@code phone_number} /
 * {@code template} / {@code verification_code}).
 *
 * <p>That matters: the wire shape stays the tenant's to decide through {@code body_mapping_rules}.
 * Naming the field here after the channel rather than after the existing schema is what made a
 * delegated phone challenge post {@code {"phone": ...}} to a service that documents {@code
 * phone_number}.
 */
public class ContactExecutionRequest {

  Map<String, Object> values;

  /**
   * Allow-listed projection of the authenticated user, exposed as top-level {@code $.user.*}.
   *
   * <p>Kept out of {@link #values} for the same reason {@code AuthenticationExecutionRequest} does
   * it (Issue #1439): the executor reads this field, so nothing in the request body can spoof the
   * trusted projection.
   */
  Map<String, Object> user;

  public ContactExecutionRequest() {
    this.values = new HashMap<>();
  }

  public ContactExecutionRequest(Map<String, Object> values) {
    this.values = values == null ? new HashMap<>() : values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public String optValueAsString(String key, String defaultValue) {
    Object value = values.get(key);
    if (value instanceof String string && !string.isEmpty()) {
      return string;
    }
    return defaultValue;
  }

  public void setUser(Map<String, Object> user) {
    this.user = user;
  }

  public Map<String, Object> user() {
    return user;
  }

  public boolean hasUser() {
    return user != null && !user.isEmpty();
  }
}
