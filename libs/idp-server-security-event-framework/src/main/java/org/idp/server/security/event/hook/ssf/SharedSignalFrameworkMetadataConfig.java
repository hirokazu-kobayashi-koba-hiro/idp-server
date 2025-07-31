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

import java.util.List;
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

  public String defaultSubjects() {
    return defaultSubjects;
  }
}
