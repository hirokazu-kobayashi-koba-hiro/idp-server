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

package org.idp.server.core.oidc.identity;

public enum UserStatus {
  UNREGISTERED("Account has not been created"),
  REGISTERED("Registered but email not verified"),
  IDENTITY_VERIFIED("Identity verified"),
  IDENTITY_VERIFICATION_REQUIRED("Identity verification (ekyc) required"),
  ACTIVATED("Account activated"),
  LOCKED("Temporarily locked due to failures"),
  DISABLED("Disabled by user or admin"),
  SUSPENDED("Suspended due to policy violations"),
  DEACTIVATED("Deactivation requested, in grace period"),
  DELETED_PENDING("Pending deletion after grace period"),
  DELETED("Permanently deleted");

  String description;

  UserStatus(String description) {
    this.description = description;
  }

  public String description() {
    return description;
  }

  public static UserStatus of(String string) {
    for (UserStatus userStatus : UserStatus.values()) {
      if (userStatus.name().equalsIgnoreCase(string)) {
        return userStatus;
      }
    }
    return UNREGISTERED;
  }

  public boolean isIdentityVerified() {
    return this == IDENTITY_VERIFIED;
  }
}
