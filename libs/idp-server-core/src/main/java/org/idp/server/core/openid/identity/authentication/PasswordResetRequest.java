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
 * Password reset request.
 *
 * <p>Unlike {@link PasswordChangeRequest}, this request does not require the current password
 * because the user has already been authenticated through an alternative method (e.g., email
 * verification).
 *
 * <p>This is used when a user has forgotten their password and needs to reset it after completing
 * an authentication flow with the {@code password:reset} scope.
 *
 * @see PasswordChangeRequest
 */
public record PasswordResetRequest(String newPassword) {

  public PasswordResetRequest(Map<String, Object> values) {
    this((String) values.getOrDefault("new_password", ""));
  }

  public boolean hasNewPassword() {
    return newPassword != null && !newPassword.isEmpty();
  }

  public Map<String, Object> toMap() {
    return Map.of("new_password", newPassword);
  }
}
