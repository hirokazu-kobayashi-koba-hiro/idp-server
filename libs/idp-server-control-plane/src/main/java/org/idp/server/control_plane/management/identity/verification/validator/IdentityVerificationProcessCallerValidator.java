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

package org.idp.server.control_plane.management.identity.verification.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationProcessCaller;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationProcessConfiguration;

/**
 * Refuses a process whose {@code caller} contradicts itself (Issue #1966).
 *
 * <p>At runtime such a process is never wrong in a dangerous direction: an unknown caller runs from
 * no endpoint, and {@code basic_auth} on an {@code end_user} process is simply never checked. But
 * both read as something they are not — a typo that silently disables the process, or credentials
 * that look like they protect it — so they are stopped when the configuration is saved.
 */
public class IdentityVerificationProcessCallerValidator {

  IdentityVerificationConfiguration configuration;

  public IdentityVerificationProcessCallerValidator(
      IdentityVerificationConfiguration configuration) {
    this.configuration = configuration;
  }

  public void validate() {
    List<String> errors = new ArrayList<>();
    if (configuration.processes() != null) {
      for (Map.Entry<String, IdentityVerificationProcessConfiguration> entry :
          configuration.processes().entrySet()) {
        collectErrors(entry.getKey(), entry.getValue(), errors);
      }
    }
    throwExceptionIfInvalid(errors);
  }

  private void collectErrors(
      String processName, IdentityVerificationProcessConfiguration process, List<String> errors) {
    IdentityVerificationProcessCaller caller = process.caller();

    if (caller == IdentityVerificationProcessCaller.unknown) {
      errors.add(
          String.format(
              "processes.%s.caller must be end_user or external_service: %s",
              processName, process.toMap().get("caller")));
    }

    if (caller == IdentityVerificationProcessCaller.end_user && process.hasBasicAuth()) {
      errors.add(
          String.format(
              "processes.%s.request.basic_auth is not checked for an end_user process. Remove it, or set caller to external_service",
              processName));
    }
  }

  void throwExceptionIfInvalid(List<String> errors) {
    if (!errors.isEmpty()) {
      throw new InvalidRequestException(
          "identity verification configuration validation is failed", errors);
    }
  }
}
