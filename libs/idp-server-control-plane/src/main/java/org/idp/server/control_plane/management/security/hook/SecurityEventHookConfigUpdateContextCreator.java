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

import java.util.List;
import java.util.Map;

import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigurationRequest;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookRequest;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfigurationIdentifier;

public class SecurityEventHookConfigUpdateContextCreator {

  Tenant tenant;
  SecurityEventHookConfiguration before;
  SecurityEventHookConfigurationIdentifier identifier;
  SecurityEventHookRequest request;
  boolean dryRun;
  JsonConverter jsonConverter;

  public SecurityEventHookConfigUpdateContextCreator(
      Tenant tenant,
      SecurityEventHookConfiguration before,
      SecurityEventHookConfigurationIdentifier identifier,
      SecurityEventHookRequest request,
      boolean dryRun) {
    this.tenant = tenant;
    this.before = before;
    this.identifier = identifier;
    this.request = request;
    this.dryRun = dryRun;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public SecurityEventHookConfigUpdateContext create() {
    SecurityEventHookConfigurationRequest configurationRequest = jsonConverter.read(request.toMap(), SecurityEventHookConfigurationRequest.class);

    SecurityEventHookConfiguration after =
       configurationRequest.toConfiguration(identifier.value());

    return new SecurityEventHookConfigUpdateContext(tenant, before, after, dryRun);
  }
}
