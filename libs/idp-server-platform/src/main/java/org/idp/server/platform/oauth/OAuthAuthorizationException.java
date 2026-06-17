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

package org.idp.server.platform.oauth;

/**
 * Thrown when acquiring an OAuth 2.0 access token from an external service's token endpoint fails
 * (non-2xx response). This is distinct from a network/transport failure ({@code
 * HttpNetworkErrorException}): the token endpoint <em>responded</em>, but rejected the request —
 * typically a misconfigured {@code oauth_authorization} (e.g. an invalid {@code client_id}).
 *
 * <p>Carrying the upstream {@code statusCode}/{@code responseBody} lets the boundary handler log
 * the cause for diagnosis while returning a meaningful, non-500 error to the caller. The response
 * body is for server-side logging only and must not be echoed to the API client (it is the external
 * service's internal error representation).
 */
public class OAuthAuthorizationException extends RuntimeException {

  int statusCode;
  String responseBody;

  public OAuthAuthorizationException(int statusCode, String responseBody) {
    super("OAuth token request failed: " + statusCode);
    this.statusCode = statusCode;
    this.responseBody = responseBody;
  }

  public int statusCode() {
    return statusCode;
  }

  public String responseBody() {
    return responseBody;
  }
}
