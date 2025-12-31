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

public class BackChannelNotificationResult {

  private final boolean success;
  private final int httpStatusCode;
  private final String errorMessage;
  private final boolean timeout;

  private BackChannelNotificationResult(
      boolean success, int httpStatusCode, String errorMessage, boolean timeout) {
    this.success = success;
    this.httpStatusCode = httpStatusCode;
    this.errorMessage = errorMessage;
    this.timeout = timeout;
  }

  public static BackChannelNotificationResult success(int httpStatusCode) {
    return new BackChannelNotificationResult(true, httpStatusCode, null, false);
  }

  public static BackChannelNotificationResult failure(int httpStatusCode, String errorMessage) {
    return new BackChannelNotificationResult(false, httpStatusCode, errorMessage, false);
  }

  public static BackChannelNotificationResult timeout(String errorMessage) {
    return new BackChannelNotificationResult(false, 0, errorMessage, true);
  }

  public boolean isSuccess() {
    return success;
  }

  public int httpStatusCode() {
    return httpStatusCode;
  }

  public String errorMessage() {
    return errorMessage;
  }

  public boolean isTimeout() {
    return timeout;
  }

  public boolean shouldRetry() {
    if (timeout) {
      return true;
    }
    return httpStatusCode >= 500;
  }
}
