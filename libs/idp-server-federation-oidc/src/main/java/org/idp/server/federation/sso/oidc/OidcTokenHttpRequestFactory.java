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
package org.idp.server.federation.sso.oidc;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import org.idp.server.platform.http.HttpQueryParams;
import org.idp.server.platform.oauth.client.OutboundClientAuthentication;
import org.idp.server.platform.oauth.client.OutboundClientAuthenticators;

/**
 * Builds the token request to the external IdP: the code exchange's parameters, with the client
 * authentication on top.
 */
public class OidcTokenHttpRequestFactory {

  OutboundClientAuthenticators authenticators = new OutboundClientAuthenticators();

  public HttpRequest create(OidcTokenRequest tokenRequest) throws URISyntaxException {
    OutboundClientAuthentication authentication =
        authenticators.authenticate(tokenRequest.credentials());
    HttpQueryParams httpQueryParams =
        new HttpQueryParams(authentication.addTo(tokenRequest.toGrantValues()));

    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(new URI(tokenRequest.endpoint()))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(httpQueryParams.params()));
    authentication.applyTo(builder);
    return builder.build();
  }
}
