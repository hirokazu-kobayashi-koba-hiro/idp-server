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

package org.idp.server.core.openid.token;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.token.handler.token.io.TokenRequestResponse;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.openid.token.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public interface TokenApi {

  TokenRequestResponse request(
      TenantIdentifier tenantId,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert,
      List<String> dpopProofHeaders,
      RequestAttributes requestAttributes);

  TokenIntrospectionResponse inspect(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes);

  /**
   * Introspect a token with full sender-constraint verification (RS forwarding pattern).
   *
   * <p>The Resource Server forwards the artifacts the Client presented at the resource endpoint via
   * the request body parameters: {@code client_cert} (mTLS, RFC 8705), {@code dpop_proof} (DPoP,
   * RFC 9449) and the corresponding {@code dpop_htm} / {@code dpop_htu}. The {@code clientCert}
   * parameter passed here is the RS's own TLS client certificate used to authenticate to the AS,
   * which is independent from the token-binding cert.
   */
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
