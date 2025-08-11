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

package org.idp.server.control_plane.management.authentication.configuration;

import java.util.UUID;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigRequest;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigurationRequest;
import org.idp.server.core.oidc.authentication.config.AuthenticationConfiguration;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthenticationConfigRegistrationContextCreator {

  Tenant tenant;
  AuthenticationConfigRequest request;
  boolean dryRun;
  JsonConverter jsonConverter;

  public AuthenticationConfigRegistrationContextCreator(
      Tenant tenant, AuthenticationConfigRequest request, boolean dryRun) {
    this.tenant = tenant;
    this.request = request;
    this.dryRun = dryRun;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public AuthenticationConfigRegistrationContext create() {

    AuthenticationConfigurationRequest configurationRequest =
        jsonConverter.read(request.toMap(), AuthenticationConfigurationRequest.class);

    String id =
        configurationRequest.hasId() ? configurationRequest.id() : UUID.randomUUID().toString();

    AuthenticationConfiguration configuration = configurationRequest.toConfiguration(id);

    return new AuthenticationConfigRegistrationContext(tenant, configuration, dryRun);
  }
}
