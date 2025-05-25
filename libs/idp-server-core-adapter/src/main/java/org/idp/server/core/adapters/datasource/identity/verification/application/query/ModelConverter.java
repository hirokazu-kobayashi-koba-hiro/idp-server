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

package org.idp.server.core.adapters.datasource.identity.verification.application.query;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.*;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplicationDetails;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowDelegation;
import org.idp.server.core.extension.identity.verification.trustframework.TrustFramework;
import org.idp.server.core.oidc.identity.UserIdentifier;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class ModelConverter {

  static IdentityVerificationApplication convert(Map<String, String> map) {

    IdentityVerificationApplicationIdentifier identifier =
        new IdentityVerificationApplicationIdentifier(map.get("id"));
    TenantIdentifier tenantIdentifier = new TenantIdentifier(map.get("tenant_id"));
    RequestedClientId requestedClientId = new RequestedClientId(map.get("client_id"));
    IdentityVerificationType verificationType =
        new IdentityVerificationType(map.get("verification_type"));
    UserIdentifier sub = new UserIdentifier(map.get("user_id"));
    IdentityVerificationApplicationDetails details =
        new IdentityVerificationApplicationDetails(
            JsonNodeWrapper.fromString(map.get("application_details")));

    ExternalWorkflowDelegation externalWorkflowDelegation =
        new ExternalWorkflowDelegation(map.get("external_workflow_delegation"));
    ExternalWorkflowApplicationIdentifier externalApplicationId =
        new ExternalWorkflowApplicationIdentifier(map.get("external_application_id"));
    ExternalWorkflowApplicationDetails externalWorkflowApplicationDetails =
        new ExternalWorkflowApplicationDetails(
            JsonNodeWrapper.fromString(map.get("external_application_details")));

    TrustFramework trustFramework = new TrustFramework(map.get("trust_framework"));
    IdentityVerificationExaminationResults examinationResults = toExaminationResults(map);
    IdentityVerificationApplicationProcesses processes = toProcesses(map);

    IdentityVerificationApplicationStatus status =
        IdentityVerificationApplicationStatus.of(map.get("status"));
    LocalDateTime requestedAt = LocalDateTime.parse(map.get("requested_at"));

    return new IdentityVerificationApplication(
        identifier,
        verificationType,
        tenantIdentifier,
        requestedClientId,
        sub,
        details,
        externalWorkflowDelegation,
        externalApplicationId,
        externalWorkflowApplicationDetails,
        trustFramework,
        examinationResults,
        processes,
        status,
        requestedAt);
  }

  private static IdentityVerificationExaminationResults toExaminationResults(
      Map<String, String> map) {
    if (map.get("examination_results") == null || map.get("examination_results").isEmpty()) {
      return new IdentityVerificationExaminationResults();
    }
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(map.get("examination_results"));
    List<IdentityVerificationExaminationResult> examinationResultList = new ArrayList<>();

    for (JsonNodeWrapper wrapper : jsonNodeWrapper.elements()) {
      examinationResultList.add(new IdentityVerificationExaminationResult(wrapper));
    }

    return new IdentityVerificationExaminationResults(examinationResultList);
  }

  static IdentityVerificationApplicationProcesses toProcesses(Map<String, String> map) {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(map.get("processes"));
    List<IdentityVerificationApplicationProcess> processList = new ArrayList<>();

    for (JsonNodeWrapper wrapper : jsonNodeWrapper.elements()) {
      String process = wrapper.getValueOrEmptyAsString("process");
      String requestedAt = wrapper.getValueOrEmptyAsString("requested_at");
      IdentityVerificationApplicationProcess applicationProcess =
          new IdentityVerificationApplicationProcess(process, requestedAt);
      processList.add(applicationProcess);
    }

    return new IdentityVerificationApplicationProcesses(processList);
  }
}
