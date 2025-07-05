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
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplicationIdParam;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowDelegation;
import org.idp.server.core.extension.identity.verification.exception.IdentityVerificationApplicationConfigurationNotFoundException;
import org.idp.server.platform.http.HmacAuthenticationConfiguration;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.platform.uuid.UuidConvertable;

public class IdentityVerificationConfiguration implements JsonReadable, UuidConvertable {
  String id;
  String type;
  String delegation;
  String description;
  String externalWorkflowDelegation;
  String externalWorkflowApplicationIdParam;
  OAuthAuthorizationConfiguration oauthAuthorization = new OAuthAuthorizationConfiguration();
  HmacAuthenticationConfiguration hmacAuthentication = new HmacAuthenticationConfiguration();
  Map<String, IdentityVerificationProcessConfiguration> processes;
  List<String> approvedTargetTypes = new ArrayList<>();
  IdentityVerificationVerifiedClaimsConfiguration verifiedClaimsConfiguration =
      new IdentityVerificationVerifiedClaimsConfiguration();

  public IdentityVerificationConfiguration() {}

  public IdentityVerificationConfiguration(
      String id,
      String type,
      String delegation,
      String description,
      String externalWorkflowDelegation,
      String externalWorkflowApplicationIdParam,
      OAuthAuthorizationConfiguration oauthAuthorization,
      HmacAuthenticationConfiguration hmacAuthentication,
      Map<String, IdentityVerificationProcessConfiguration> processes,
      List<String> approvedTargetTypes,
      IdentityVerificationVerifiedClaimsConfiguration verifiedClaimsConfiguration) {
    this.id = id;
    this.type = type;
    this.delegation = delegation;
    this.description = description;
    this.externalWorkflowDelegation = externalWorkflowDelegation;
    this.externalWorkflowApplicationIdParam = externalWorkflowApplicationIdParam;
    this.oauthAuthorization = oauthAuthorization;
    this.hmacAuthentication = hmacAuthentication;
    this.processes = processes;
    this.approvedTargetTypes = approvedTargetTypes;
    this.verifiedClaimsConfiguration = verifiedClaimsConfiguration;
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

  public String delegation() {
    return delegation;
  }

  public String description() {
    return description;
  }

  public ExternalWorkflowDelegation externalWorkflowDelegation() {
    return new ExternalWorkflowDelegation(externalWorkflowDelegation);
  }

  public ExternalWorkflowApplicationIdParam externalWorkflowApplicationIdParam() {
    return new ExternalWorkflowApplicationIdParam(externalWorkflowApplicationIdParam);
  }

  public boolean hasOAuthAuthorization() {
    return oauthAuthorization != null && oauthAuthorization.exists();
  }

  public OAuthAuthorizationConfiguration oauthAuthorization() {
    if (oauthAuthorization == null) {
      return new OAuthAuthorizationConfiguration();
    }
    return oauthAuthorization;
  }

  public boolean hasHmacAuthentication() {
    return hmacAuthentication != null && hmacAuthentication.exists();
  }

  public HmacAuthenticationConfiguration hmacAuthentication() {
    if (hmacAuthentication == null) {
      return new HmacAuthenticationConfiguration();
    }
    return hmacAuthentication;
  }

  public Map<String, IdentityVerificationProcessConfiguration> processes() {
    return processes;
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

    return oauthAuthorization();
  }

  public HmacAuthenticationConfiguration getHmacAuthenticationConfig(
      IdentityVerificationProcess process) {
    IdentityVerificationProcessConfiguration processConfig = getProcessConfig(process);

    if (processConfig.hasHmacAuthentication()) {
      return processConfig.hmacAuthentication();
    }

    return hmacAuthentication();
  }

  public List<String> approvedTargetTypes() {
    return approvedTargetTypes;
  }

  public String approvedTargetTypesAsString() {
    return String.join(",", approvedTargetTypes);
  }

  public IdentityVerificationVerifiedClaimsConfiguration verifiedClaimsConfiguration() {
    return verifiedClaimsConfiguration;
  }

  public boolean hasVerifiedClaimsConfiguration() {
    return verifiedClaimsConfiguration != null && verifiedClaimsConfiguration.exists();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    map.put("type", type);
    map.put("delegation", delegation);
    map.put("description", description);
    map.put("external_workflow_delegation", externalWorkflowDelegation);
    map.put("externalWorkflowApplicationIdParam", externalWorkflowApplicationIdParam);
    if (hasOAuthAuthorization()) map.put("oauth_authorization", oauthAuthorization.toMap());
    if (hasHmacAuthentication()) map.put("hmac_authentication", hmacAuthentication.toMap());
    map.put("processes", processes);
    map.put("approved_target_types", approvedTargetTypes);
    if (hasVerifiedClaimsConfiguration())
      map.put("verified_claims_configuration", verifiedClaimsConfiguration.toMap());

    return map;
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }
}
