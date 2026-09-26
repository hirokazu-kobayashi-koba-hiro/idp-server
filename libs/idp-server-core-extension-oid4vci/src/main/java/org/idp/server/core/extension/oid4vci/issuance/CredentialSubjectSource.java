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
package org.idp.server.core.extension.oid4vci.issuance;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.path.JsonPathWrapper;

/**
 * What a credential's claims may be taken from: the user's OpenID Connect claims, their verified
 * claims and their custom properties.
 *
 * <p>An allowlist rather than the whole user: the user record also holds the password hash, the
 * authentication devices, roles and tenant assignments, and a mistyped {@code from} must not be
 * able to put any of those into a credential that leaves the server for good.
 */
public class CredentialSubjectSource {

  static final Set<String> AVAILABLE =
      Set.of(
          "sub",
          "name",
          "given_name",
          "family_name",
          "middle_name",
          "nickname",
          "preferred_username",
          "profile",
          "picture",
          "website",
          "email",
          "email_verified",
          "gender",
          "birthdate",
          "zoneinfo",
          "locale",
          "phone_number",
          "phone_number_verified",
          "address",
          "verified_claims",
          "custom_properties");

  JsonPathWrapper document;

  public CredentialSubjectSource(User user) {
    Map<String, Object> available = new HashMap<>();
    user.toMap()
        .forEach(
            (name, value) -> {
              if (AVAILABLE.contains(name) && value != null) {
                available.put(name, value);
              }
            });
    this.document = new JsonPathWrapper(JsonConverter.snakeCaseInstance().write(available));
  }

  /** The value at a JSONPath, or {@code null} when the user has none. */
  public Object read(String path) {
    return document.readRaw(path);
  }
}
