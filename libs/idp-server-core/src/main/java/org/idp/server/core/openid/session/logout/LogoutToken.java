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

package org.idp.server.core.openid.session.logout;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.core.openid.session.ClientSessionIdentifier;

public class LogoutToken {

  private static final String BACKCHANNEL_LOGOUT_EVENT =
      "http://schemas.openid.net/event/backchannel-logout";
  private static final String JTI_PREFIX = "lt_";

  private final String iss;
  private final String sub;
  private final String aud;
  private final long iat;
  private final String jti;
  private final String sid;
  private final Map<String, Object> events;

  public LogoutToken() {
    this.iss = "";
    this.sub = "";
    this.aud = "";
    this.iat = 0;
    this.jti = "";
    this.sid = "";
    this.events = new HashMap<>();
  }

  public LogoutToken(
      String iss,
      String sub,
      String aud,
      long iat,
      String jti,
      String sid,
      Map<String, Object> events) {
    this.iss = iss;
    this.sub = sub;
    this.aud = aud;
    this.iat = iat;
    this.jti = jti;
    this.sid = sid;
    this.events = events;
  }

  public static LogoutToken create(
      String issuer, String clientId, String sub, ClientSessionIdentifier sid) {
    Map<String, Object> events = new HashMap<>();
    events.put(BACKCHANNEL_LOGOUT_EVENT, new HashMap<>());

    return new LogoutToken(
        issuer,
        sub,
        clientId,
        Instant.now().getEpochSecond(),
        JTI_PREFIX + UUID.randomUUID().toString(),
        sid != null ? sid.value() : null,
        events);
  }

  public String iss() {
    return iss;
  }

  public String sub() {
    return sub;
  }

  public String aud() {
    return aud;
  }

  public long iat() {
    return iat;
  }

  public String jti() {
    return jti;
  }

  public String sid() {
    return sid;
  }

  public Map<String, Object> events() {
    return events;
  }

  public boolean hasSub() {
    return sub != null && !sub.isEmpty();
  }

  public boolean hasSid() {
    return sid != null && !sid.isEmpty();
  }

  public boolean hasSubOrSid() {
    return hasSub() || hasSid();
  }

  public boolean hasBackchannelLogoutEvent() {
    return events != null && events.containsKey(BACKCHANNEL_LOGOUT_EVENT);
  }

  public Map<String, Object> toClaimsMap() {
    Map<String, Object> claims = new HashMap<>();
    claims.put("iss", iss);
    if (hasSub()) {
      claims.put("sub", sub);
    }
    claims.put("aud", aud);
    claims.put("iat", iat);
    claims.put("jti", jti);
    if (hasSid()) {
      claims.put("sid", sid);
    }
    claims.put("events", events);
    return claims;
  }

  public static LogoutToken fromClaimsMap(Map<String, Object> claims) {
    String iss = (String) claims.get("iss");
    String sub = (String) claims.get("sub");
    String aud = claims.get("aud") instanceof String ? (String) claims.get("aud") : null;
    long iat = claims.get("iat") instanceof Number ? ((Number) claims.get("iat")).longValue() : 0;
    String jti = (String) claims.get("jti");
    String sid = (String) claims.get("sid");
    @SuppressWarnings("unchecked")
    Map<String, Object> events = (Map<String, Object>) claims.get("events");

    return new LogoutToken(iss, sub, aud, iat, jti, sid, events);
  }
}
