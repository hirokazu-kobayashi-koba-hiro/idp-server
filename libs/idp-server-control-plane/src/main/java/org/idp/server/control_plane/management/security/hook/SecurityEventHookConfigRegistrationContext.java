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

package org.idp.server.control_plane.management.security.hook;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementResponse;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementStatus;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.SecurityEventHookConfiguration;

public class SecurityEventHookConfigRegistrationContext {

  Tenant tenant;
  SecurityEventHookConfiguration securityEventHookConfiguration;
  boolean dryRun;

  public SecurityEventHookConfigRegistrationContext(
      Tenant tenant,
      SecurityEventHookConfiguration securityEventHookConfiguration,
      boolean dryRun) {
    this.tenant = tenant;
    this.securityEventHookConfiguration = securityEventHookConfiguration;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public SecurityEventHookConfiguration configuration() {
    return securityEventHookConfiguration;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public SecurityEventHookConfigManagementResponse toResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("result", securityEventHookConfiguration.payload());
    response.put("dry_run", dryRun);
    return new SecurityEventHookConfigManagementResponse(
        SecurityEventHookConfigManagementStatus.CREATED, response);
  }
}
