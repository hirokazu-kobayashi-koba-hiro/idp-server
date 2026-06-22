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

package org.idp.server.core.openid.authentication.evaluator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserRole;

/**
 * Creates the allow-listed {@code $.user.*} node of the authentication policy evaluation context
 * (used by success / failure / lock conditions). The context is a JSON map consumed by JsonPath, so
 * {@link #create(User)} returns a {@code Map} rather than a typed value object.
 *
 * <p>This is intentionally an authentication-policy concern rather than a {@link User} method: the
 * decision of <em>which</em> identity attributes are safe to expose to condition evaluation is a
 * policy / security judgement and is owned here, next to the evaluator that consumes it. {@link
 * User} stays a plain identity entity and exposes only its public accessors.
 *
 * <p><b>Issue #1501 (why an allow list):</b> a policy condition compares an operand authored by
 * tenant admins against this context, and the boolean outcome is observable through the
 * authentication flow. Exposing an attribute here therefore turns the evaluation into a
 * value-extraction oracle over that attribute. The projection is an explicit <b>allow list</b>
 * (fail-safe: a newly added {@link User} field stays hidden until it is deliberately added here)
 * and excludes attributes the policy author cannot already read in cleartext ({@code
 * hashed_password}, {@code credentials}, {@code verified_claims}).
 *
 * <p>{@code roles} (projected as role names), {@code permissions} and {@code custom_properties} are
 * exposed because they are tenant-managed authorization data the admin can already read via the
 * user management API, so the oracle grants no additional read capability. Role names and
 * permissions are flat string lists so conditions can use the {@code contains} operator, e.g.
 * {@code {"path": "$.user.roles", "operation": "contains", "value": "admin"}}.
 *
 * <p>This projection is scoped to in-process policy evaluation and must never be sent to external
 * endpoints (cf. Issue #1439).
 */
public class PolicyEvaluationUserContextCreator {

  /**
   * @return allow-listed user attributes, or an empty map when the user is null or does not exist
   */
  public static Map<String, Object> create(User user) {
    Map<String, Object> map = new HashMap<>();
    // OPSession#user() returns a raw nullable field (OIDCSessionVerifier passes it directly),
    // unlike
    // the other call sites that use the User.notFound() sentinel. Guard null here so every path is
    // covered without depending on each caller to normalize.
    if (user == null || !user.exists()) {
      return map;
    }
    map.put("sub", user.sub());
    map.put("status", user.status().name());
    map.put("provider_id", user.providerId());
    map.put("has_password", user.hasPassword());
    if (user.hasEmailVerified()) map.put("email_verified", user.emailVerified());
    if (user.hasPhoneNumberVerified()) map.put("phone_number_verified", user.phoneNumberVerified());
    if (user.hasRoles()) map.put("roles", user.roles().stream().map(UserRole::roleName).toList());
    if (user.hasPermissions()) map.put("permissions", new ArrayList<>(user.permissions()));
    if (user.hasCustomProperties())
      map.put("custom_properties", new HashMap<>(user.customPropertiesValue()));
    return map;
  }
}
