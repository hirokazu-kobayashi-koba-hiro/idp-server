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

package org.idp.server.control_plane.management.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Hides secrets in an audit log as the management API returns it.
 *
 * <p>An audit log keeps the request and the resource before and after the operation as they were,
 * so it can hold a client secret, a password a user was created with, an authorization server's
 * private keys or the credential of a push notification service. The log itself is left as it is;
 * only what the API returns is masked, wherever the key appears, however deeply nested.
 *
 * <p>Keys are matched whole, ignoring case. A prefix or substring match would also hide fields that
 * only describe a secret ({@code client_secret_expires_at}, {@code token_endpoint}), and a key such
 * as {@code client_secret} would not start with {@code secret} anyway.
 */
public class AuditLogResponseMask {

  static final String MASKED = "[SCRUBBED]";

  static final Set<String> KEYS =
      Set.of(
          // passwords
          "password",
          "raw_password",
          "hashed_password",
          "current_password",
          "new_password",
          // shared secrets and keys
          "secret",
          "client_secret",
          "api_secret",
          "device_secret",
          "api_key",
          "private_key",
          "key_content",
          "credential",
          "jwks",
          "client_assertion",
          // tokens
          "token",
          "access_token",
          "refresh_token",
          "id_token",
          "oneshot_token",
          // values carried in HTTP headers
          "authorization",
          "cookie",
          "auth_proof",
          "x-view-binding");

  /**
   * @return a copy of {@code values} with the value of every masked key replaced, in nested objects
   *     and arrays too
   */
  public Map<String, Object> apply(Map<String, Object> values) {
    if (values == null) {
      return null;
    }
    Map<String, Object> masked = new LinkedHashMap<>();
    values.forEach((key, value) -> masked.put(key, isMasked(key) ? MASKED : maskNested(value)));
    return masked;
  }

  private Object maskNested(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> nested = new LinkedHashMap<>();
      map.forEach((key, item) -> nested.put(String.valueOf(key), item));
      return apply(nested);
    }
    if (value instanceof List<?> list) {
      List<Object> items = new ArrayList<>();
      list.forEach(item -> items.add(maskNested(item)));
      return items;
    }
    return value;
  }

  private static boolean isMasked(String key) {
    return key != null && KEYS.contains(key.toLowerCase(Locale.ROOT));
  }
}
