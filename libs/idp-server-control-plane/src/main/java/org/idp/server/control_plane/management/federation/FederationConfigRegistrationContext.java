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

package org.idp.server.control_plane.management.federation;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.ConfigRegistrationContext;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementResponse;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementStatus;
import org.idp.server.core.oidc.federation.FederationConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class FederationConfigRegistrationContext implements ConfigRegistrationContext {

  Tenant tenant;
  FederationConfiguration federationConfiguration;
  boolean dryRun;

  public FederationConfigRegistrationContext(
      Tenant tenant, FederationConfiguration federationConfiguration, boolean dryRun) {
    this.tenant = tenant;
    this.federationConfiguration = federationConfiguration;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public FederationConfiguration configuration() {
    return federationConfiguration;
  }

  @Override
  public String type() {
    return configuration().type().name();
  }

  @Override
  public Map<String, Object> payload() {
    return federationConfiguration.payload();
  }

  @Override
  public boolean isDryRun() {
    return dryRun;
  }

  public FederationConfigManagementResponse toResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("result", federationConfiguration.toMap());
    response.put("dry_run", dryRun);
    return new FederationConfigManagementResponse(
        FederationConfigManagementStatus.CREATED, response);
  }
}
