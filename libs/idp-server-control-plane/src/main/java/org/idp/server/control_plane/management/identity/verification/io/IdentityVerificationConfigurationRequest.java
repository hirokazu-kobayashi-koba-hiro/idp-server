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

package org.idp.server.control_plane.management.identity.verification.io;

import java.util.*;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationRegistrationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationVerifiedClaimsConfiguration;
import org.idp.server.platform.http.HmacAuthenticationConfiguration;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;

public class IdentityVerificationConfigurationRequest implements JsonReadable {
  String id;
  String type;
  String delegation;
  String description;
  String externalService;
  String externalApplicationIdParam;
  OAuthAuthorizationConfiguration oauthAuthorization;
  HmacAuthenticationConfiguration hmacAuthentication;
  Map<String, IdentityVerificationProcessConfiguration> processes;
  IdentityVerificationRegistrationConfiguration registration;
  List<String> approvedTargetTypes = new ArrayList<>();
  IdentityVerificationVerifiedClaimsConfiguration verifiedClaimsConfiguration;

  public IdentityVerificationConfigurationRequest() {}

  public String id() {
    return id;
  }

  public boolean hasId() {
    return id != null && !id.isEmpty();
  }

  public IdentityVerificationType type() {
    return new IdentityVerificationType(type);
  }

  public String delegation() {
    return delegation;
  }

  public String description() {
    return description;
  }

  public IdentityVerificationConfiguration toConfiguration(String identifier) {
    return new IdentityVerificationConfiguration(
        identifier,
        type,
        delegation,
        description,
        externalService,
        externalApplicationIdParam,
        oauthAuthorization,
        hmacAuthentication,
        processes,
        registration,
        approvedTargetTypes,
        verifiedClaimsConfiguration);
  }
}
