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

public class LogoutTokenValidationResult {

  private final boolean valid;
  private final LogoutToken logoutToken;
  private final String errorCode;
  private final String errorDescription;

  private LogoutTokenValidationResult(
      boolean valid, LogoutToken logoutToken, String errorCode, String errorDescription) {
    this.valid = valid;
    this.logoutToken = logoutToken;
    this.errorCode = errorCode;
    this.errorDescription = errorDescription;
  }

  public static LogoutTokenValidationResult success(LogoutToken logoutToken) {
    return new LogoutTokenValidationResult(true, logoutToken, null, null);
  }

  public static LogoutTokenValidationResult failure(String errorCode, String errorDescription) {
    return new LogoutTokenValidationResult(false, null, errorCode, errorDescription);
  }

  public boolean isValid() {
    return valid;
  }

  public LogoutToken logoutToken() {
    return logoutToken;
  }

  public String errorCode() {
    return errorCode;
  }

  public String errorDescription() {
    return errorDescription;
  }
}
