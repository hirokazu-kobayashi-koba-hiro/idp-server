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

package org.idp.server.security.event.hook.ssf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.mapper.MappingRule;

public class SharedSignalFrameworkTransmissionConfig implements JsonReadable {

  String securityEventTypeIdentifier;
  String url;
  Map<String, Object> securityEventTokenHeaders = new HashMap<>();
  String kid;
  List<MappingRule> securityEventTokenAdditionalPayloadMappingRules = new ArrayList<>();

  public SharedSignalFrameworkTransmissionConfig() {}

  public SharedSignalFrameworkTransmissionConfig(
      String url, Map<String, Object> securityEventTokenHeaders, String kid) {
    this.url = url;
    this.securityEventTokenHeaders = securityEventTokenHeaders;
    this.kid = kid;
  }

  public String url() {
    return url;
  }

  public Map<String, Object> securityEventTokenHeaders() {
    if (securityEventTokenHeaders == null) {
      return new HashMap<>();
    }
    return securityEventTokenHeaders;
  }

  public String kid() {
    return kid;
  }

  public List<MappingRule> securityEventTokenAdditionalPayloadMappingRules() {
    if (securityEventTokenAdditionalPayloadMappingRules == null) {
      return new ArrayList<>();
    }
    return securityEventTokenAdditionalPayloadMappingRules;
  }

  public SecurityEventTypeIdentifier securityEventTypeIdentifier() {
    return new SecurityEventTypeIdentifier(securityEventTypeIdentifier);
  }
}
