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

public class SharedSignalFrameworkMetadataConfig implements JsonReadable {
  String specVersion;
  String issuer;
  String jwks;
  String jwksUri;
  List<String> deliveryMethodsSupported;
  String configurationEndpoint;
  String statusEndpoint;
  String addSubjectEndpoint;
  String removeSubjectEndpoint;
  String verificationEndpoint;
  List<String> criticalSubjectMembers;
  List<AuthorizationSchemeConfig> authorizationSchemes;
  String defaultSubjects;
  StreamConfiguration streamConfiguration;

  public SharedSignalFrameworkMetadataConfig() {}

  public String specVersion() {
    return specVersion;
  }

  public String issuer() {
    return issuer;
  }

  public String jwks() {
    return jwks;
  }

  public String jwksUri() {
    return jwksUri;
  }

  public List<String> deliveryMethodsSupported() {
    return deliveryMethodsSupported;
  }

  public String configurationEndpoint() {
    return configurationEndpoint;
  }

  public String statusEndpoint() {
    return statusEndpoint;
  }

  public String addSubjectEndpoint() {
    return addSubjectEndpoint;
  }

  public String removeSubjectEndpoint() {
    return removeSubjectEndpoint;
  }

  public String verificationEndpoint() {
    return verificationEndpoint;
  }

  public List<String> criticalSubjectMembers() {
    return criticalSubjectMembers;
  }

  public List<AuthorizationSchemeConfig> authorizationSchemes() {
    return authorizationSchemes;
  }

  public List<Map<String, Object>> authorizationSchemesAsMap() {
    if (authorizationSchemes == null) {
      return new ArrayList<>();
    }
    return authorizationSchemes.stream().map(AuthorizationSchemeConfig::toMap).toList();
  }

  public String defaultSubjects() {
    return defaultSubjects;
  }

  public StreamConfiguration streamConfiguration() {
    return streamConfiguration;
  }

  public boolean hasStreamConfiguration() {
    return streamConfiguration != null;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("spec_version", specVersion);
    map.put("issuer", issuer);
    map.put("jwks", jwks);
    map.put("jwks_uri", jwksUri);
    map.put("delivery_methods_supported", deliveryMethodsSupported);
    map.put("configuration_endpoint", configurationEndpoint);
    map.put("status_endpoint", statusEndpoint);
    map.put("add_subject_endpoint", addSubjectEndpoint);
    map.put("remove_subject_endpoint", removeSubjectEndpoint);
    map.put("verification_endpoint", verificationEndpoint);
    map.put("critical_subject_members", criticalSubjectMembers);
    map.put("authorization_schemes", authorizationSchemesAsMap());
    map.put("default_subjects", defaultSubjects);
    if (streamConfiguration != null) {
      map.put("stream_configuration", streamConfiguration.toMap());
    }
    return map;
  }

  public Map<String, Object> toConfigMap() {
    Map<String, Object> map = toMap();
    map.remove("jwks");
    return map;
  }
}
