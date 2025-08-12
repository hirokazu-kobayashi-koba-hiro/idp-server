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

package org.idp.server.platform.notification.email;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class SmtpEmailSenderConfig implements JsonReadable {
  String host;
  String port;
  String username;
  String password;

  public SmtpEmailSenderConfig() {}

  public String host() {
    return host;
  }

  public String port() {
    return port;
  }

  public String username() {
    return username;
  }

  public String password() {
    return password;
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> result = new HashMap<>();
    result.put("host", host);
    result.put("port", port);
    result.put("username", username);
    result.put("password", password);
    return result;
  }
}
