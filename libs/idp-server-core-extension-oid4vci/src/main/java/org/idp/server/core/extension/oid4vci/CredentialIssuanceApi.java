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
import org.idp.server.core.extension.oid4vci.io.CredentialNonceResponse;
import org.idp.server.core.extension.oid4vci.io.CredentialResponse;
import org.idp.server.platform.http.HttpRequestInputs;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/** The Credential Issuer's endpoints a Wallet calls (OpenID4VCI 1.0 Sections 7 and 8). */
public interface CredentialIssuanceApi {

  CredentialNonceResponse issueNonce(TenantIdentifier tenantIdentifier);

  CredentialResponse requestCredential(
      TenantIdentifier tenantIdentifier, HttpRequestInputs inputs, Map<String, Object> body);
}
