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

package org.idp.server.core.extension.identity.verification.application.pre_hook.verification;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfig;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationProcessConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.process.ProcessDependencies;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Verifies process execution order and dependencies for identity verification applications.
 *
 * <p>This verifier enforces three types of constraints:
 *
 * <ol>
 *   <li><b>Sequential Dependency</b>: Ensures required processes are completed before execution
 *   <li><b>Retry Control</b>: Prevents duplicate execution when retry is not allowed
 *   <li><b>Status Restriction</b>: Limits execution to specific application statuses
 * </ol>
 *
 * <p><b>Configuration Example:</b>
 *
 * <pre>{@code
 * {
 *   "processes": {
 *     "apply": {
 *       "dependencies": {
 *         "required_processes": [],
 *         "allow_retry": false
 *       }
 *     },
 *     "crm-registration": {
 *       "dependencies": {
 *         "required_processes": ["apply"],
 *         "allow_retry": false
 *       }
 *     },
 *     "cancel": {
 *       "dependencies": {
 *         "allow_during_statuses": ["REQUESTED", "APPLYING"]
 *       }
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p><b>Error Messages:</b>
 *
 * <ul>
 *   <li>Missing dependency: "Process 'crm-registration' requires completion of: apply. Missing:
 *       apply"
 *   <li>Retry denied: "Process 'apply' does not allow retry and has already been executed"
 *   <li>Status restriction: "Process 'cancel' cannot be executed during status: APPROVED. Allowed
 *       statuses: REQUESTED, APPLYING"
 * </ul>
 *
 * <p><b>Integration:</b>
 *
 * <p>This verifier is automatically registered in {@link
 * IdentityVerificationApplicationRequestVerifiers} and executed as part of the pre-hook validation
 * phase.
 *
 * @see ProcessDependencies
 * @see IdentityVerificationApplicationRequestVerifiers
 */
public class ProcessSequenceVerifier implements IdentityVerificationApplicationRequestVerifier {

  private final IdentityVerificationConfigurationQueryRepository configurationRepository;

  public ProcessSequenceVerifier(
      IdentityVerificationConfigurationQueryRepository configurationRepository) {
    this.configurationRepository = configurationRepository;
  }

  @Override
  public String type() {
    return "process_sequence";
  }

  @Override
  public IdentityVerificationApplicationRequestVerifiedResult verify(
      Tenant tenant,
      User user,
      IdentityVerificationApplication currentApplication,
      IdentityVerificationApplications previousApplications,
      IdentityVerificationType type,
      IdentityVerificationProcess process,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfig verificationConfig) {

    // Get full configuration to access process dependencies
    IdentityVerificationConfiguration configuration = configurationRepository.get(tenant, type);
    IdentityVerificationProcessConfiguration processConfig =
        configuration.getProcessConfig(process);
    ProcessDependencies dependencies = processConfig.dependencies();

    List<String> errors = new ArrayList<>();

    // 1. Check required process dependencies
    if (dependencies.hasRequiredProcesses()) {
      List<String> missing = findMissingProcesses(currentApplication, dependencies);
      if (!missing.isEmpty()) {
        errors.add(
            String.format(
                "Process '%s' requires completion of: %s. Missing: %s",
                process.name(),
                String.join(", ", dependencies.requiredProcesses()),
                String.join(", ", missing)));
      }
    }

    // 2. Check retry restriction
    if (!dependencies.allowRetry() && currentApplication.hasExecutedProcess(process.name())) {
      errors.add(
          String.format(
              "Process '%s' does not allow retry and has already been executed", process.name()));
    }

    // 3. Check status restrictions
    if (dependencies.hasStatusRestrictions()) {
      String currentStatus = currentApplication.status().value();
      if (!dependencies.isAllowedDuringStatus(currentStatus)) {
        errors.add(
            String.format(
                "Process '%s' cannot be executed during status: %s. Allowed statuses: %s",
                process.name(),
                currentStatus,
                String.join(", ", dependencies.allowDuringStatuses())));
      }
    }

    if (!errors.isEmpty()) {
      return IdentityVerificationApplicationRequestVerifiedResult.failure(errors);
    }

    return IdentityVerificationApplicationRequestVerifiedResult.success();
  }

  /**
   * Find processes that are required but not yet completed.
   *
   * @param application Current application state
   * @param dependencies Process dependencies configuration
   * @return List of missing process names (empty if all required processes are completed)
   */
  private List<String> findMissingProcesses(
      IdentityVerificationApplication application, ProcessDependencies dependencies) {
    List<String> missing = new ArrayList<>();

    for (String requiredProcess : dependencies.requiredProcesses()) {
      if (!application.hasCompletedProcess(requiredProcess)) {
        missing.add(requiredProcess);
      }
    }

    return missing;
  }
}
