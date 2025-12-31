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

public enum LogoutNotificationStatus {
  PENDING("pending"),
  SUCCESS("success"),
  FAILED("failed"),
  TIMEOUT("timeout");

  private final String value;

  LogoutNotificationStatus(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean isSuccess() {
    return this == SUCCESS;
  }

  public boolean isFailed() {
    return this == FAILED || this == TIMEOUT;
  }

  public boolean isPending() {
    return this == PENDING;
  }

  public static LogoutNotificationStatus of(String value) {
    for (LogoutNotificationStatus status : values()) {
      if (status.value.equals(value)) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unknown notification status: " + value);
  }
}
