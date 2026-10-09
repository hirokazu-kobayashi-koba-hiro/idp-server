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
 *
 * <p>A secret can also sit on the value side of a mapping rule: {@code {"static_value": "Bearer
 * ...", "to": "Authorization"}} sends a fixed value to a header or field named by {@code to}. Such
 * a rule has its {@code static_value} masked when {@code to} names a masked key or a header that
 * carries a credential. Only the target is judged, by the same whole-name match; a secret written
 * into a function's arguments (a {@code format} template holding a fixed token, say) is not
 * recognised and stays as it is.
 *
 * <p>Security events have their own list ({@code SecurityEventLogConfiguration}'s essential scrub
 * keys), matched by prefix and extended per tenant. The two are kept apart because they match
 * differently; a key added to one is worth checking against the other.
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
          "x-view-binding",
          // values that work as credentials on their own
          "incoming_webhook_url",
          "notification_token");

  /**
   * Header names, besides the masked keys, that carry a credential: masked as keys (a map of
   * headers) and as the target of a mapping rule's fixed value.
   */
  static final Set<String> CREDENTIAL_HEADERS =
      Set.of(
          "x-api-key",
          "api-key",
          "apikey",
          "x-auth-token",
          "x-access-token",
          "x-api-token",
          "x-client-secret",
          "ocp-apim-subscription-key",
          "x-functions-key",
          "proxy-authorization");

  static final String STATIC_VALUE = "static_value";
  static final String TO = "to";

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
    if (masked.containsKey(STATIC_VALUE) && sendsToCredential(values.get(TO))) {
      masked.put(STATIC_VALUE, MASKED);
    }
    return masked;
  }

  /**
   * Whether a mapping rule's {@code to} names where a credential goes: a masked key or a credential
   * header, as the last part of a path ({@code credentials.client_secret}) too.
   */
  private static boolean sendsToCredential(Object to) {
    if (!(to instanceof String target) || target.isEmpty()) {
      return false;
    }
    String name = target.substring(target.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    return KEYS.contains(name) || CREDENTIAL_HEADERS.contains(name);
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
    if (key == null) {
      return false;
    }
    String name = key.toLowerCase(Locale.ROOT);
    return KEYS.contains(name) || CREDENTIAL_HEADERS.contains(name);
  }
}
