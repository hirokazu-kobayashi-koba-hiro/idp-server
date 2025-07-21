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

package org.idp.server.core.extension.identity.verification.configuration;

import java.util.*;
import org.idp.server.core.extension.identity.exception.IdentityVerificationApplicationConfigurationNotFoundException;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.configuration.common.IdentityVerificationCommonConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationProcessConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.registration.IdentityVerificationRegistrationConfig;
import org.idp.server.core.extension.identity.verification.configuration.verified_claims.IdentityVerificationVerifiedClaimsConfiguration;
import org.idp.server.platform.http.HmacAuthenticationConfiguration;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.platform.uuid.UuidConvertable;

public class IdentityVerificationConfiguration implements JsonReadable, UuidConvertable {
  String id;
  String type;
  String description;
  IdentityVerificationCommonConfiguration common = new IdentityVerificationCommonConfiguration();
  Map<String, IdentityVerificationProcessConfiguration> processes = new HashMap<>();
  IdentityVerificationRegistrationConfig registration =
      new IdentityVerificationRegistrationConfig();
  IdentityVerificationVerifiedClaimsConfiguration verifiedClaims =
      new IdentityVerificationVerifiedClaimsConfiguration();

  public IdentityVerificationConfiguration() {}

  public IdentityVerificationConfiguration(
      String id,
      String type,
      String description,
      IdentityVerificationCommonConfiguration common,
      Map<String, IdentityVerificationProcessConfiguration> processes,
      IdentityVerificationRegistrationConfig registration,
      IdentityVerificationVerifiedClaimsConfiguration verifiedClaims) {
    this.id = id;
    this.type = type;
    this.description = description;
    this.common = common;
    this.processes = processes;
    this.registration = registration;
    this.verifiedClaims = verifiedClaims;
  }

  public String id() {
    return id;
  }

  public UUID idAsUuid() {
    return convertUuid(id);
  }

  public IdentityVerificationType type() {
    return new IdentityVerificationType(type);
  }

  public String description() {
    return description;
  }

  public boolean hasCommon() {
    return common != null;
  }

  public IdentityVerificationCommonConfiguration common() {
    if (common == null) {
      return new IdentityVerificationCommonConfiguration();
    }
    return common;
  }

  public String getCallbackApplicationId(IdentityVerificationProcess process) {

    return common().callbackApplicationIdParam();
  }

  public OAuthAuthorizationConfiguration oauthAuthorizationFromCommon() {
    if (common == null) {
      return new OAuthAuthorizationConfiguration();
    }

    if (common.hasOAuthAuthorization()) {
      return common.oAuthAuthorization();
    }
    return new OAuthAuthorizationConfiguration();
  }

  public HmacAuthenticationConfiguration hmacAuthenticationFromCommon() {
    if (common == null) {
      return new HmacAuthenticationConfiguration();
    }

    if (common.hasHmacAuthentication()) {
      return common.hmacAuthentication();
    }

    return new HmacAuthenticationConfiguration();
  }

  public Map<String, IdentityVerificationProcessConfiguration> processes() {
    return processes;
  }

  public boolean hasProcesses() {
    return processes != null && !processes.isEmpty();
  }

  public IdentityVerificationProcessConfiguration getProcessConfig(
      IdentityVerificationProcess process) {
    if (!processes.containsKey(process.name())) {
      throw new IdentityVerificationApplicationConfigurationNotFoundException(
          "invalid configuration. type: " + process.name() + " is unregistered.");
    }
    return processes.get(process.name());
  }

  public OAuthAuthorizationConfiguration getOAuthAuthorizationConfig(
      IdentityVerificationProcess process) {
    IdentityVerificationProcessConfiguration processConfig = getProcessConfig(process);

    if (processConfig.hasOAuthAuthorization()) {
      return processConfig.oauthAuthorization();
    }

    return oauthAuthorizationFromCommon();
  }

  public HmacAuthenticationConfiguration getHmacAuthenticationConfig(
      IdentityVerificationProcess process) {
    IdentityVerificationProcessConfiguration processConfig = getProcessConfig(process);

    if (processConfig.hasHmacAuthentication()) {
      return processConfig.hmacAuthentication();
    }

    return hmacAuthenticationFromCommon();
  }

  public IdentityVerificationVerifiedClaimsConfiguration verifiedClaimsConfiguration() {
    if (verifiedClaims == null) {
      return new IdentityVerificationVerifiedClaimsConfiguration();
    }
    return verifiedClaims;
  }

  public boolean hasVerifiedClaims() {
    return verifiedClaims != null && verifiedClaims.exists();
  }

  public IdentityVerificationRegistrationConfig registration() {
    return registration;
  }

  public boolean hasRegistration() {
    return registration != null;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    map.put("type", type);
    map.put("description", description);
    if (hasCommon()) map.put("common", common.toMap());
    if (hasProcesses()) map.put("processes", processes);
    if (hasRegistration()) map.put("registration", registration);
    if (hasVerifiedClaims()) map.put("verified_claims", verifiedClaims.toMap());

    return map;
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }
}
