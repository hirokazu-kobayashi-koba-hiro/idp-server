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

package org.idp.server.security.event.hook.ssf;

import java.util.List;
import java.util.Map;

/**
 * SET
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8417">SET</a>
 */
public class SecurityEventTokenEntity {
  String issuer;
  List<String> audience;
  SharedSecurityEvent sharedSecurityEvent;

  public SecurityEventTokenEntity() {}

  public SecurityEventTokenEntity(
      String issuer, List<String> audience, SharedSecurityEvent sharedSecurityEvent) {
    this.issuer = issuer;
    this.audience = audience;
    this.sharedSecurityEvent = sharedSecurityEvent;
  }

  public String issuerValue() {
    return issuer;
  }

  public List<String> audience() {
    return audience;
  }

  /**
   * Returns the audience value for JWT claim.
   *
   * <p>Per RFC 7519 Section 4.1.3, the "aud" claim MAY be a string or an array of strings.
   *
   * @return single string if audience has one element, otherwise the list
   */
  public Object audienceValue() {
    if (audience == null || audience.isEmpty()) {
      return null;
    }
    if (audience.size() == 1) {
      return audience.get(0);
    }
    return audience;
  }

  public boolean hasAudience() {
    return audience != null && !audience.isEmpty();
  }

  public SharedSecurityEvent securityEvent() {
    return sharedSecurityEvent;
  }

  public Map<String, Object> eventAsMap() {
    return sharedSecurityEvent.eventAsMap();
  }
}
