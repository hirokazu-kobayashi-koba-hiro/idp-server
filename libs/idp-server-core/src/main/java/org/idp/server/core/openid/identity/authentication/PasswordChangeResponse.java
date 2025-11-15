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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.identity.User;

/** Password change response. */
public class PasswordChangeResponse {

  PasswordChangeStatus status;
  Map<String, Object> contents;

  public PasswordChangeResponse(PasswordChangeStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public static PasswordChangeResponse success(User user) {
    return new PasswordChangeResponse(
        PasswordChangeStatus.SUCCESS, Map.of("message", "Password changed successfully."));
  }

  public static PasswordChangeResponse invalidCurrentPassword() {
    Map<String, Object> contents = new HashMap<>();
    contents.put("error", "invalid_current_password");
    contents.put("error_description", "Current password is incorrect.");
    return new PasswordChangeResponse(PasswordChangeStatus.INVALID_CURRENT_PASSWORD, contents);
  }

  public static PasswordChangeResponse invalidNewPassword(String reason) {
    Map<String, Object> contents = new HashMap<>();
    contents.put("error", "invalid_new_password");
    contents.put("error_description", reason);
    return new PasswordChangeResponse(PasswordChangeStatus.INVALID_NEW_PASSWORD, contents);
  }

  public static PasswordChangeResponse invalidRequest(String reason) {
    Map<String, Object> contents = new HashMap<>();
    contents.put("error", "invalid_request");
    contents.put("error_description", reason);
    return new PasswordChangeResponse(PasswordChangeStatus.INVALID_REQUEST, contents);
  }

  public static PasswordChangeResponse insufficientScope(Map<String, Object> contents) {
    return new PasswordChangeResponse(PasswordChangeStatus.FORBIDDEN, contents);
  }

  public PasswordChangeStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public boolean isSuccess() {
    return status.isSuccess();
  }

  public Map<String, Object> contents() {
    return contents;
  }
}
