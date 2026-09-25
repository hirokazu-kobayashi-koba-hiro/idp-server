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
import org.idp.server.core.extension.oid4vci.handler.CredentialIssuerMetadataHandler;
import org.idp.server.core.extension.oid4vci.io.CredentialIssuerMetadataResponse;
import org.idp.server.core.extension.oid4vci.io.CredentialIssuerMetadataStatus;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.dependency.protocol.DefaultAuthorizationProvider;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class DefaultOid4vciProtocol implements Oid4vciProtocol {

  CredentialIssuerMetadataHandler credentialIssuerMetadataHandler;
  LoggerWrapper log = LoggerWrapper.getLogger(DefaultOid4vciProtocol.class);

  public DefaultOid4vciProtocol(
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository) {
    this.credentialIssuerMetadataHandler =
        new CredentialIssuerMetadataHandler(authorizationServerConfigurationQueryRepository);
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
}
