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

package org.idp.server.platform.type;

import java.util.Map;
import org.idp.server.platform.http.BasicAuth;
import org.idp.server.platform.http.BasicAuthConvertable;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.security.type.Action;
import org.idp.server.platform.security.type.IpAddress;
import org.idp.server.platform.security.type.Resource;
import org.idp.server.platform.security.type.UserAgent;

public class RequestAttributes implements BasicAuthConvertable {

  JsonNodeWrapper jsonNodeWrapper;

  public RequestAttributes() {}

  public RequestAttributes(Map<String, Object> values) {
    this.jsonNodeWrapper = JsonNodeWrapper.fromMap(values);
  }

  public Map<String, Object> toMap() {
    return jsonNodeWrapper.toMap();
  }

  public boolean exists() {
    return jsonNodeWrapper != null && jsonNodeWrapper.exists();
  }

  public String optValueAsString(String key, String defaultValue) {
    if (!containsKey(key)) {
      return defaultValue;
    }
    return jsonNodeWrapper.getValueOrEmptyAsString(key);
  }

  public boolean containsKey(String key) {
    if (jsonNodeWrapper == null) {
      return false;
    }
    return jsonNodeWrapper.contains(key);
  }

  public String getValueOrEmptyAsString(String key) {
    return jsonNodeWrapper.getValueOrEmptyAsString(key);
  }

  public boolean hasIpAddress() {
    return containsKey("ip_address");
  }

  public IpAddress getIpAddress() {
    return new IpAddress(getValueOrEmptyAsString("ip_address"));
  }

  public boolean hasUserAgent() {
    return containsKey("user_agent");
  }

  public UserAgent getUserAgent() {
    return new UserAgent(getValueOrEmptyAsString("user_agent"));
  }

  public Resource resource() {
    return new Resource(getValueOrEmptyAsString("resource"));
  }

  public Action action() {
    return new Action(getValueOrEmptyAsString("action"));
  }

  public JsonNodeWrapper getJsonNode(String key) {
    return jsonNodeWrapper.getNode(key);
  }

  public BasicAuth basicAuth() {
    JsonNodeWrapper headersNode = getJsonNode("headers");
    String authorization = headersNode.getValueOrEmptyAsString("authorization");
    return convertBasicAuth(authorization);
  }

  public boolean hasBasicAuth() {
    BasicAuth basicAuth = basicAuth();
    return basicAuth.exists();
  }
}
