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

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;

/**
 * The authentication behind the access token, projected for {@code ContactChangeRule} evaluation
 * (Issue #1416).
 *
 * <p>Carries what the token can attest about how its holder authenticated, under the names the
 * protocol already uses, so a tenant writes {@code $.amr} and {@code $.acr} rather than learning a
 * second vocabulary. The {@code amr} values are this server's own ({@code password}, {@code email},
 * {@code sms}, {@code fido-uaf}, ...), not the RFC 8176 registry names:
 *
 * <pre>{@code
 * { "amr": ["password"], "acr": "urn:...", "auth_time": 1789000000, "auth_age": 42 }
 * }</pre>
 *
 * <p>{@code auth_time} and {@code auth_age} are omitted when the grant carries no authentication
 * time. That absence is meaningful: a rule with {@code max_auth_age_seconds} cannot be satisfied by
 * a token that never recorded when its holder authenticated, and treating a missing time as "just
 * now" would invert the rule.
 */
public class ContactChangeAuthenticationContext {

  Authentication authentication;

  public ContactChangeAuthenticationContext(Authentication authentication) {
    this.authentication = authentication == null ? new Authentication() : authentication;
  }

  public List<String> methods() {
    return authentication.methods() == null ? new ArrayList<>() : authentication.methods();
  }

  public boolean hasAuthenticationTime() {
    return authentication.hasAuthenticationTime();
  }

  /** Seconds elapsed since the holder authenticated. Only meaningful when a time is recorded. */
  public long authAgeSeconds() {
    if (!hasAuthenticationTime()) {
      return Long.MAX_VALUE;
    }
    return Duration.between(authentication.time(), SystemDateTime.now()).getSeconds();
  }

  public boolean withinMaxAuthAge(int maxAuthAgeSeconds) {
    if (maxAuthAgeSeconds <= 0) {
      return true;
    }
    if (!hasAuthenticationTime()) {
      return false;
    }
    return authAgeSeconds() <= maxAuthAgeSeconds;
  }

  public JsonPathWrapper toJsonPath() {
    Map<String, Object> context = new HashMap<>();
    context.put("amr", methods());
    context.put("acr", authentication.acr());
    if (hasAuthenticationTime()) {
      context.put("auth_time", SystemDateTime.toEpochSecond(authentication.time()));
      context.put("auth_age", authAgeSeconds());
    }
    return new JsonPathWrapper(JsonNodeWrapper.fromMap(context).toJson());
  }
}
