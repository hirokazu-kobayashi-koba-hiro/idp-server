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

package org.idp.server.core.openid.identity.authentication;

import java.util.Map;

/**
 * Password change request.
 *
 * <p>Following industry standards (Microsoft Entra ID, Okta), this request only requires the
 * current password and new password. Password confirmation is handled on the client side for UX
 * purposes.
 *
 * @see <a href="https://learn.microsoft.com/en-us/graph/api/user-changepassword">Microsoft Graph
 *     API: changePassword</a>
 */
public record PasswordChangeRequest(String currentPassword, String newPassword) {

  public PasswordChangeRequest(Map<String, Object> values) {
    this(
        (String) values.getOrDefault("current_password", ""),
        (String) values.getOrDefault("new_password", ""));
  }

  public boolean hasCurrentPassword() {
    return currentPassword != null && !currentPassword.isEmpty();
  }

  public boolean hasNewPassword() {
    return newPassword != null && !newPassword.isEmpty();
  }

  public Map<String, Object> toMap() {
    return Map.of("current_password", currentPassword, "new_password", newPassword);
  }
}
