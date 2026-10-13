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

package org.idp.server.authentication.interactors.fido2;

import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;

/**
 * The username a FIDO2 credential is bound to.
 *
 * <p>Registration stores this value on the credential, and deregistration compares the credential's
 * owner with it. Both must resolve it the same way, so they share this class.
 */
public class Fido2Username {

  String value;

  Fido2Username(String value) {
    this.value = value;
  }

  /** Resolves the username from the user based on the tenant identity policy. */
  public static Fido2Username of(User user, TenantIdentityPolicy identityPolicy) {
    switch (identityPolicy.uniqueKeyType()) {
      case USERNAME:
      case USERNAME_OR_EXTERNAL_USER_ID:
        return new Fido2Username(user.preferredUsername());

      case EMAIL:
      case EMAIL_OR_EXTERNAL_USER_ID:
        return new Fido2Username(user.email());

      case PHONE:
      case PHONE_OR_EXTERNAL_USER_ID:
        return new Fido2Username(user.phoneNumber());

      case EXTERNAL_USER_ID:
        return new Fido2Username(user.externalUserId());

      default:
        // Fallback to preferredUsername
        return new Fido2Username(user.preferredUsername());
    }
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }
}
