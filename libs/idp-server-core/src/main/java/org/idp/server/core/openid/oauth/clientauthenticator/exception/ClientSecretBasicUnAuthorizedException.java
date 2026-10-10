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

package org.idp.server.core.openid.oauth.clientauthenticator.exception;

import java.util.Map;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;

/**
 * A failed client authentication of a client that sent its credentials in the HTTP {@code
 * Authorization} header with the Basic scheme.
 *
 * <p><a href="https://www.rfc-editor.org/rfc/rfc6749#section-5.2">RFC 6749 Section 5.2</a>: "If the
 * client attempted to authenticate via the "Authorization" request header field, the authorization
 * server MUST respond with an HTTP 401 (Unauthorized) status code and include the
 * "WWW-Authenticate" response header field matching the authentication scheme used by the client."
 *
 * <p>The failure carries the Basic challenge, which every error handler copies into the response.
 */
public class ClientSecretBasicUnAuthorizedException extends ClientUnAuthorizedException {

  public static final String HEADER_NAME = "WWW-Authenticate";

  private String realm;

  /**
   * @param realm the protection space of the challenge (RFC 7617 Section 2): the tenant's issuer
   */
  public ClientSecretBasicUnAuthorizedException(
      String method, RequestedClientId clientId, String reason, String realm) {
    super(method, clientId, reason);
    this.realm = realm;
  }

  @Override
  public Map<String, String> responseHeaders() {
    return Map.of(HEADER_NAME, String.format("Basic realm=\"%s\"", realm));
  }
}
