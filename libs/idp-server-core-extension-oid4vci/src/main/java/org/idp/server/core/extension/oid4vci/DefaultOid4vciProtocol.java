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
package org.idp.server.core.extension.oid4vci;

import java.util.Map;
import org.idp.server.core.extension.oid4vci.exception.CredentialRequestInvalidException;
import org.idp.server.core.extension.oid4vci.handler.CredentialHandler;
import org.idp.server.core.extension.oid4vci.handler.CredentialIssuerMetadataHandler;
import org.idp.server.core.extension.oid4vci.handler.CredentialNonceHandler;
import org.idp.server.core.extension.oid4vci.io.CredentialIssuerMetadataResponse;
import org.idp.server.core.extension.oid4vci.io.CredentialIssuerMetadataStatus;
import org.idp.server.core.extension.oid4vci.io.CredentialNonceResponse;
import org.idp.server.core.extension.oid4vci.io.CredentialRequest;
import org.idp.server.core.extension.oid4vci.io.CredentialResponse;
import org.idp.server.core.extension.oid4vci.nonce.CredentialNonceRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.dpop.DPoPProofInvalidException;
import org.idp.server.core.openid.token.repository.OAuthTokenQueryRepository;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenInsufficientScopeException;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenInvalidException;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.dependency.protocol.DefaultAuthorizationProvider;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class DefaultOid4vciProtocol implements Oid4vciProtocol {

  CredentialIssuerMetadataHandler credentialIssuerMetadataHandler;
  CredentialNonceHandler credentialNonceHandler;
  CredentialHandler credentialHandler;
  LoggerWrapper log = LoggerWrapper.getLogger(DefaultOid4vciProtocol.class);

  public DefaultOid4vciProtocol(
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      CredentialNonceRepository credentialNonceRepository,
      OAuthTokenQueryRepository oAuthTokenQueryRepository,
      UserQueryRepository userQueryRepository) {
    this.credentialIssuerMetadataHandler =
        new CredentialIssuerMetadataHandler(authorizationServerConfigurationQueryRepository);
    this.credentialNonceHandler =
        new CredentialNonceHandler(
            authorizationServerConfigurationQueryRepository, credentialNonceRepository);
    this.credentialHandler =
        new CredentialHandler(
            oAuthTokenQueryRepository,
            authorizationServerConfigurationQueryRepository,
            credentialNonceRepository,
            userQueryRepository);
  }

  @Override
  public AuthorizationProvider authorizationProtocolProvider() {
    return DefaultAuthorizationProvider.idp_server.toAuthorizationProtocolProvider();
  }

  @Override
  public CredentialIssuerMetadataResponse getMetadata(Tenant tenant) {
    try {
      return credentialIssuerMetadataHandler.getMetadata(tenant);
    } catch (Exception exception) {
      log.error(exception.getMessage(), exception);
      return new CredentialIssuerMetadataResponse(
          CredentialIssuerMetadataStatus.SERVER_ERROR, Map.of("error", "server_error"));
    }
  }

  @Override
  public CredentialNonceResponse issueNonce(Tenant tenant) {
    try {
      return credentialNonceHandler.handle(tenant);
    } catch (Exception exception) {
      log.error(exception.getMessage(), exception);
      return CredentialNonceResponse.serverError();
    }
  }

  /**
   * The boundary where a failure becomes a response: request errors of Section 8.3.1.2 are 400,
   * token errors are RFC 6750 (Section 8.3.1.1), and anything else is this server's own.
   */
  @Override
  public CredentialResponse requestCredential(CredentialRequest request) {
    try {
      return credentialHandler.handle(request);
    } catch (CredentialRequestInvalidException exception) {
      log.info("credential request rejected: {} {}", exception.error(), exception.getMessage());
      return CredentialResponse.requestError(exception.error(), exception.errorDescription());
    } catch (TokenInvalidException | DPoPProofInvalidException exception) {
      log.info("credential request with invalid token: {}", exception.getMessage());
      return CredentialResponse.invalidToken(exception.getMessage());
    } catch (TokenInsufficientScopeException exception) {
      log.info("credential request with insufficient scope: {}", exception.getMessage());
      return CredentialResponse.insufficientScope(exception.getMessage());
    } catch (Exception exception) {
      log.error(exception.getMessage(), exception);
      return CredentialResponse.serverError();
    }
  }
}
