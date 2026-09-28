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

package org.idp.server.core.openid.clientinstance;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.idp.server.platform.date.LocalDateTimeParser;

/**
 * Search conditions of the Client Instance management list, across the clients of a tenant.
 *
 * <p>An operator comes with what is at hand rather than a client: a user asking about their
 * devices, a key, or — when an attestation key or intermediate is found compromised — the serial of
 * a certificate the instances were attested through.
 */
public class ClientInstanceQueries {

  static final int DEFAULT_LIMIT = 20;
  static final int MAX_LIMIT = 1000;

  Map<String, String> values;

  public ClientInstanceQueries() {
    this.values = new HashMap<>();
  }

  public ClientInstanceQueries(Map<String, String> values) {
    this.values = Objects.requireNonNullElseGet(values, HashMap::new);
  }

  /** The same conditions, with {@code client_id} replaced. */
  public ClientInstanceQueries withClientId(String clientId) {
    Map<String, String> replaced = new HashMap<>(values);
    replaced.put("client_id", clientId);
    return new ClientInstanceQueries(replaced);
  }

  public boolean hasClientId() {
    return has("client_id");
  }

  public String clientId() {
    return values.get("client_id");
  }

  public boolean hasUserId() {
    return has("user_id");
  }

  public String userId() {
    return values.get("user_id");
  }

  public boolean hasStatus() {
    return has("status");
  }

  public ClientInstanceStatus status() {
    return ClientInstanceStatus.of(values.get("status"));
  }

  /**
   * Past {@code expires_at} or not. Separate from {@link #status()}, which is the stored state: an
   * instance past its expiry stays active there, yet no longer authenticates. {@code status=active}
   * with {@code expired=false} finds the instances that can authenticate now.
   */
  public boolean hasExpired() {
    return has("expired");
  }

  public boolean expired() {
    return Boolean.parseBoolean(values.get("expired"));
  }

  /** True when {@code expired} is given but is neither {@code true} nor {@code false}. */
  public boolean hasInvalidExpired() {
    return hasExpired()
        && !values.get("expired").equals("true")
        && !values.get("expired").equals("false");
  }

  public boolean hasRevocationReason() {
    return has("revocation_reason");
  }

  public ClientInstanceRevocationReason revocationReason() {
    return ClientInstanceRevocationReason.of(values.get("revocation_reason"));
  }

  /** The platform of the attestation the instance was registered with. */
  public boolean hasPlatform() {
    return has("platform");
  }

  public String platform() {
    return values.get("platform");
  }

  /**
   * A certificate of the attestation chain, by serial number. Lowercase hexadecimal as recorded, so
   * a serial copied from a revocation list in uppercase still matches.
   */
  public boolean hasCertificateSerial() {
    return has("certificate_serial");
  }

  public String certificateSerial() {
    return values.get("certificate_serial").toLowerCase(Locale.ROOT);
  }

  public boolean hasInstanceKeyThumbprint() {
    return has("instance_key_thumbprint");
  }

  public String instanceKeyThumbprint() {
    return values.get("instance_key_thumbprint");
  }

  /** Registered at or after. */
  public boolean hasFrom() {
    return has("from");
  }

  public LocalDateTime from() {
    return LocalDateTimeParser.parse(values.get("from"));
  }

  /** Registered at or before. */
  public boolean hasTo() {
    return has("to");
  }

  public LocalDateTime to() {
    return LocalDateTimeParser.parse(values.get("to"));
  }

  /** True when limit or offset is given but is not a non-negative number. */
  public boolean hasInvalidPaging() {
    return isInvalidNonNegative("limit") || isInvalidNonNegative("offset");
  }

  private boolean isInvalidNonNegative(String key) {
    if (!has(key)) {
      return false;
    }
    try {
      return Integer.parseInt(values.get(key)) < 0;
    } catch (NumberFormatException e) {
      return true;
    }
  }

  public int limit() {
    if (!has("limit")) {
      return DEFAULT_LIMIT;
    }
    try {
      return Math.min(Integer.parseInt(values.get("limit")), MAX_LIMIT);
    } catch (NumberFormatException e) {
      return DEFAULT_LIMIT;
    }
  }

  public int offset() {
    if (!has("offset")) {
      return 0;
    }
    try {
      return Integer.parseInt(values.get("offset"));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  public Map<String, String> toMap() {
    return values;
  }

  private boolean has(String key) {
    String value = values.get(key);
    return value != null && !value.isEmpty();
  }
}
