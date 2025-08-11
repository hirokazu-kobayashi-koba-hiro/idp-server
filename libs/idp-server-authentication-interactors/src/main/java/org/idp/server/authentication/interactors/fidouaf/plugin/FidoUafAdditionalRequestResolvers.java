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

package org.idp.server.authentication.interactors.fidouaf.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.openid.authentication.AuthenticationInteractionType;
import org.idp.server.core.openid.authentication.AuthenticationTransaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class FidoUafAdditionalRequestResolvers {

  List<FidoUafAdditionalRequestResolver> resolvers;
  LoggerWrapper log = LoggerWrapper.getLogger(FidoUafAdditionalRequestResolvers.class);

  public FidoUafAdditionalRequestResolvers(List<FidoUafAdditionalRequestResolver> resolvers) {
    this.resolvers = resolvers;
  }

  public Map<String, Object> resolveAll(
      Tenant tenant,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      AuthenticationTransaction transaction) {
    Map<String, Object> response = new HashMap<>();

    for (FidoUafAdditionalRequestResolver resolver : resolvers) {
      if (resolver.shouldResolve(tenant, type, request, transaction)) {
        log.info(
            String.format(
                "Execute FidoUafAuthenticationChallengeAdditionalRequestResolver %s",
                resolver.getClass().getName()));
        Map<String, Object> resolved = resolver.resolve(tenant, type, request, transaction);
        response.putAll(resolved);
      }
    }

    return response;
  }
}
