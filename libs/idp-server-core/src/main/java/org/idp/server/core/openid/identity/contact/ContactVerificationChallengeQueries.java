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

package org.idp.server.core.openid.identity.contact;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.platform.exception.BadRequestException;
import org.idp.server.platform.exception.UnSupportedException;

/**
 * Filters for the management-side challenge listing (Issue #1416).
 *
 * <p>Exists so support can answer "the code never arrived": the operator starts from a user, and
 * needs to see where the code was sent, whether it is still valid, and how many attempts were made.
 */
public class ContactVerificationChallengeQueries {

  Map<String, String> values;

  public ContactVerificationChallengeQueries(Map<String, String> values) {
    this.values = values == null ? Map.of() : values;
  }

  public boolean hasUserId() {
    return values.containsKey("user_id") && !values.get("user_id").isBlank();
  }

  public UserIdentifier userIdentifier() {
    return new UserIdentifier(values.get("user_id"));
  }

  public boolean hasOperation() {
    return values.containsKey("operation") && !values.get("operation").isBlank();
  }

  /**
   * The operation filter.
   *
   * <p>An unknown value is the operator's typo, not a server fault, so it becomes a 400 rather than
   * being silently dropped — a filter that quietly matches nothing would read as "no challenge was
   * ever issued", which is the opposite of the answer support is looking for.
   */
  public ContactVerificationOperation operation() {
    String value = values.get("operation");
    try {
      return ContactVerificationOperation.of(value);
    } catch (UnSupportedException e) {
      throw new BadRequestException(String.format("unsupported operation filter (%s)", value));
    }
  }

  public int limit() {
    return values.containsKey("limit") ? Integer.parseInt(values.get("limit")) : 20;
  }

  /** Audit payload of the filters actually applied. */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (hasUserId()) {
      map.put("user_id", values.get("user_id"));
    }
    if (hasOperation()) {
      map.put("operation", values.get("operation"));
    }
    map.put("limit", limit());
    map.put("offset", offset());
    return map;
  }

  public int offset() {
    return values.containsKey("offset") ? Integer.parseInt(values.get("offset")) : 0;
  }
}
