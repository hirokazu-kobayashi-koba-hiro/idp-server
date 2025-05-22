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


package org.idp.server.platform.security.type;

import java.util.Map;
import java.util.function.BiConsumer;

public class RequestAttributes {

  Map<String, Object> values;

  public RequestAttributes() {}

  public RequestAttributes(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public String optValueAsString(String key, String defaultValue) {
    if (containsKey(key)) {
      return (String) values.get(key);
    }
    return defaultValue;
  }

  public boolean containsKey(String key) {
    return values.containsKey(key);
  }

  public void forEach(BiConsumer<String, Object> action) {
    values.forEach(action);
  }

  public String getValueAsString(String key) {
    return (String) values.get(key);
  }

  public boolean hasIpAddress() {
    return containsKey("ip_address");
  }

  public IpAddress getIpAddress() {
    return new IpAddress(getValueAsString("ip_address"));
  }

  public boolean hasUserAgent() {
    return containsKey("user_agent");
  }

  public UserAgent getUserAgent() {
    return new UserAgent(getValueAsString("user_agent"));
  }

  public Resource resource() {
    return new Resource(getValueAsString("resource"));
  }

  public Action action() {
    return new Action(getValueAsString("action"));
  }
}
