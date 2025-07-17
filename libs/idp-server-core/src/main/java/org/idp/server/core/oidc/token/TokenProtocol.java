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

import org.idp.server.core.oidc.token.handler.token.io.TokenRequest;
import org.idp.server.core.oidc.token.handler.token.io.TokenRequestResponse;
import org.idp.server.core.oidc.token.handler.tokenintrospection.io.TokenIntrospectionExtensionRequest;
import org.idp.server.core.oidc.token.handler.tokenintrospection.io.TokenIntrospectionInternalRequest;
import org.idp.server.core.oidc.token.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.oidc.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.oidc.token.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.oidc.token.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;

public interface TokenProtocol {

  AuthorizationProvider authorizationProtocolProvider();

  TokenRequestResponse request(TokenRequest tokenRequest);

  TokenIntrospectionResponse inspect(TokenIntrospectionRequest request);

  TokenIntrospectionResponse inspectWithVerification(TokenIntrospectionExtensionRequest request);

  TokenIntrospectionResponse inspectForInternal(TokenIntrospectionInternalRequest request);

  TokenRevocationResponse revoke(TokenRevocationRequest request);
}
