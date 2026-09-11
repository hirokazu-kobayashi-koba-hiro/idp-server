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

package org.idp.server.core.openid.identity.contact;

import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Tells the value that was just replaced that it was replaced (Issue #1416).
 *
 * <p>Separate from {@link ContactVerificationGateway} because this is not a challenge. It proves
 * nothing and gates nothing — it is a plain outbound message whose only job is to give the rightful
 * owner a chance to notice a change they did not make. Nothing about it needs the tenant's
 * authentication configuration.
 *
 * <p>It reads that configuration today only because there is nowhere else to put a message
 * template: tenant-level notification configuration does not exist yet, which is also why {@code
 * TenantInvitationManagementEntryService} still cannot send its invitation mail. Until it does, a
 * tenant that delegates verification to an external service has no template here and therefore gets
 * no notice.
 */
public interface ContactChangeNotifier {

  /**
   * Best effort: the change is already committed when this runs, so a delivery failure is logged
   * and swallowed rather than failing the operation.
   */
  void notifyChanged(
      Tenant tenant,
      ContactVerificationOperation operation,
      String previousValue,
      String newValueMasked);
}
