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
package org.idp.server.usecases.application.credential_issuer;

import java.util.Map;
import org.idp.server.core.extension.oid4vci.CredentialIssuanceApi;
import org.idp.server.core.extension.oid4vci.Oid4vciProtocol;
import org.idp.server.core.extension.oid4vci.Oid4vciProtocols;
import org.idp.server.core.extension.oid4vci.io.CredentialNonceResponse;
import org.idp.server.core.extension.oid4vci.io.CredentialRequest;
import org.idp.server.core.extension.oid4vci.io.CredentialResponse;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.http.HttpRequestInputs;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;

@Transaction
public class CredentialIssuanceEntryService implements CredentialIssuanceApi {

  TenantQueryRepository tenantQueryRepository;
  Oid4vciProtocols oid4vciProtocols;

  public CredentialIssuanceEntryService(
      TenantQueryRepository tenantQueryRepository, Oid4vciProtocols oid4vciProtocols) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.oid4vciProtocols = oid4vciProtocols;
  }

  @Override
  public CredentialNonceResponse issueNonce(TenantIdentifier tenantIdentifier) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    Oid4vciProtocol protocol = oid4vciProtocols.get(tenant.authorizationProvider());
    return protocol.issueNonce(tenant);
  }

  @Override
  public CredentialResponse requestCredential(
      TenantIdentifier tenantIdentifier, HttpRequestInputs inputs, Map<String, Object> body) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    Oid4vciProtocol protocol = oid4vciProtocols.get(tenant.authorizationProvider());
    return protocol.requestCredential(new CredentialRequest(tenant, inputs, body));
  }
}
