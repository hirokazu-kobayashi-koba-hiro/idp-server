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

package org.idp.server.control_plane.management.identity.verification;

import java.util.Map;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementResponse;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementStatus;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class IdentityVerificationConfigRegistrationContext {

  Tenant tenant;
  IdentityVerificationConfiguration identityVerificationConfiguration;
  boolean dryRun;

  public IdentityVerificationConfigRegistrationContext(
      Tenant tenant,
      IdentityVerificationConfiguration identityVerificationConfiguration,
      boolean dryRun) {
    this.tenant = tenant;
    this.identityVerificationConfiguration = identityVerificationConfiguration;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return null;
  }

  public IdentityVerificationConfiguration configuration() {
    return identityVerificationConfiguration;
  }

  public IdentityVerificationType type() {
    return identityVerificationConfiguration.type();
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public IdentityVerificationConfigManagementResponse toResponse() {
    Map<String, Object> contents =
        Map.of("result", identityVerificationConfiguration.toMap(), "dry_run", dryRun);
    return new IdentityVerificationConfigManagementResponse(
        IdentityVerificationConfigManagementStatus.CREATED, contents);
  }
}
