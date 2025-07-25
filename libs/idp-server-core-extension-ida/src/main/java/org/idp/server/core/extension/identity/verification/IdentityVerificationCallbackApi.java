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

package org.idp.server.core.extension.identity.verification;

import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.io.*;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public interface IdentityVerificationCallbackApi {

  IdentityVerificationCallbackResponse callback(
      TenantIdentifier tenantIdentifier,
      IdentityVerificationType type,
      IdentityVerificationProcess process,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes);

  IdentityVerificationCallbackResponse callback(
      TenantIdentifier tenantIdentifier,
      IdentityVerificationApplicationIdentifier identifier,
      IdentityVerificationType type,
      IdentityVerificationProcess process,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes);
}
