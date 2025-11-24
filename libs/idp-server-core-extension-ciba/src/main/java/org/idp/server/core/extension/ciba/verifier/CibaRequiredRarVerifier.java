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

package org.idp.server.core.extension.ciba.verifier;

import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.core.extension.ciba.exception.BackchannelAuthenticationBadRequestException;

/**
 * CibaRequiredRarVerifier
 *
 * <p>Validates that authorization_details (RAR) parameter is present when client configuration
 * requires it.
 *
 * <p>This verifier enables per-client enforcement of Rich Authorization Requests (RAR) as defined
 * in RFC 9396. While RAR is optional by default, clients can enable the {@code ciba_require_rar}
 * extension configuration to mandate authorization_details in all CIBA requests.
 *
 * <p>Client Extension Configuration:
 *
 * <pre>{@code
 * {
 *   "extension": {
 *     "ciba_require_rar": true
 *   }
 * }
 * }</pre>
 *
 * <p>RFC 9396 Section 4 - Authorization Request:
 *
 * <pre>
 * The authorization_details parameter is OPTIONAL in CIBA requests.
 * However, authorization servers MAY require it based on client policy.
 *
 * If required and missing, the authorization server returns the error
 * code invalid_request with error_description explaining that
 * authorization_details is required.
 * </pre>
 *
 * <p>Error Response when RAR is required but missing:
 *
 * <pre>{@code
 * HTTP/1.1 400 Bad Request
 * {
 *   "error": "invalid_request",
 *   "error_description": "authorization_details is required for this client"
 * }
 * }</pre>
 *
 * <p>Verification Logic:
 *
 * <ul>
 *   <li>Skip verification if client does not require RAR ({@code ciba_require_rar} is false)
 *   <li>Throw exception if RAR is required but authorization_details is missing
 *   <li>Pass through if authorization_details is present (content validation is handled by {@link
 *       CibaAuthorizationDetailsVerifier})
 * </ul>
 *
 * @see CibaAuthorizationDetailsVerifier Validates authorization_details content when present
 * @see
 *     org.idp.server.core.openid.oauth.configuration.client.ClientExtensionConfiguration#isCibaRequireRar()
 *     Client extension configuration
 */
public class CibaRequiredRarVerifier implements CibaExtensionVerifier {

  @Override
  public boolean shouldVerify(CibaRequestContext context) {
    return context.clientConfiguration().isCibaRequireRar();
  }

  @Override
  public void verify(CibaRequestContext context) {
    if (!context.hasAuthorizationDetails()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request", "authorization_details is required for this client");
    }
  }
}
