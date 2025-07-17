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

package org.idp.server.core.oidc.token;

import java.util.Map;
import org.idp.server.core.oidc.token.handler.token.io.TokenRequestResponse;
import org.idp.server.core.oidc.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.oidc.token.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;

public interface TokenApi {

  TokenRequestResponse request(
      TenantIdentifier tenantId,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes);

  TokenIntrospectionResponse inspect(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes);

  TokenIntrospectionResponse inspectWithVerification(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes);

  TokenRevocationResponse revoke(
      TenantIdentifier tenantId,
      Map<String, String[]> request,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes);
}
