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

package org.idp.server.core.openid.clientinstance.registration;

import java.util.Map;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Input of a platform attestation verification.
 *
 * @param tenant the tenant of the registration
 * @param clientConfiguration client configuration, carrying the expected application identity
 * @param challenge the consumed registration ticket; the evidence must be bound to its challenge
 *     value
 * @param instanceKey the Client Instance Key being registered, as a JWK map. The evidence must
 *     cover this key
 * @param evidence the raw {@code platform_evidence} object of the request
 */
public record PlatformAttestationVerificationRequest(
    Tenant tenant,
    ClientConfiguration clientConfiguration,
    ClientInstanceRegistrationChallenge challenge,
    Map<String, Object> instanceKey,
    Map<String, Object> evidence) {}
