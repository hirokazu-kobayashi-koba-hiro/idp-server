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

import org.idp.server.platform.json.JsonReadable;

public class NotificationTemplate implements JsonReadable {

  String sender;
  String title;
  String body;

  public NotificationTemplate() {}

  public NotificationTemplate(String sender, String title, String body) {
    this.sender = sender;
    this.title = title;
    this.body = body;
  }

  public String optSender(String defaultValue) {
    if (sender == null) {
      return defaultValue;
    }
    return sender;
  }

  public String optTitle(String defaultValue) {
    if (title == null) {
      return defaultValue;
    }
    return title;
  }

  public String optBody(String defaultValue) {
    if (body == null) {
      return defaultValue;
    }
    return body;
  }

  public boolean exists() {
    return title != null && body != null;
  }
}
