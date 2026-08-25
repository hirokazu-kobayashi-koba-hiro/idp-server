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

package org.idp.server.platform.notification;

import java.util.Map;

public class NotificationResult {
  boolean success;
  String channel;
  Map<String, Object> data;
  String errorMessage;

  public NotificationResult(
      boolean success, String channel, Map<String, Object> data, String errorMessage) {
    this.success = success;
    this.channel = channel;
    this.data = data;
    this.errorMessage = errorMessage;
  }

  public static NotificationResult success(String channel, Map<String, Object> data) {
    return new NotificationResult(true, channel, data, null);
  }

  public static NotificationResult failure(String channel, String errorMessage) {
    return new NotificationResult(false, channel, Map.of(), errorMessage);
  }

  public boolean isSuccess() {
    return success;
  }

  public boolean isFailure() {
    return !success;
  }

  public String channel() {
    return channel;
  }

  public Map<String, Object> data() {
    return data;
  }

  public String errorMessage() {
    return errorMessage;
  }
}
