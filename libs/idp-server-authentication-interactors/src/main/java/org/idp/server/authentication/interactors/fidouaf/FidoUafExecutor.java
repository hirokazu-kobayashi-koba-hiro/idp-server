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

package org.idp.server.authentication.interactors.fidouaf;

import org.idp.server.core.oidc.authentication.AuthorizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface FidoUafExecutor {

  FidoUafExecutorType type();

  FidoUafExecutionResult getFidoUafFacets(Tenant tenant, FidoUafConfiguration fidoUafConfiguration);

  FidoUafExecutionResult challengeRegistration(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      FidoUafExecutionRequest request,
      FidoUafConfiguration configuration);

  FidoUafExecutionResult verifyRegistration(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      FidoUafExecutionRequest request,
      FidoUafConfiguration configuration);

  FidoUafExecutionResult challengeAuthentication(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      FidoUafExecutionRequest request,
      FidoUafConfiguration configuration);

  FidoUafExecutionResult verifyAuthentication(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      FidoUafExecutionRequest request,
      FidoUafConfiguration configuration);

  FidoUafExecutionResult deleteKey(
      Tenant tenant, FidoUafExecutionRequest request, FidoUafConfiguration fidoUafConfiguration);
}
