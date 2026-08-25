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

package org.idp.server.core.openid.oauth.clientattestation.challenge;

import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Challenge endpoint of draft-ietf-oauth-attestation-based-client-auth-10 Section 6.1.
 *
 * <p>Unauthenticated by design: the endpoint hands out an opaque nonce, and the credential it will
 * be used with is only presented on the subsequent request.
 */
public interface ClientAttestationChallengeApi {

  ClientAttestationChallengeResponse issue(
      TenantIdentifier tenantIdentifier, RequestAttributes requestAttributes);
}
