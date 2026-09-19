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

package org.idp.server.control_plane.management.contact;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.contact.io.ContactVerificationChallengeManagementResponse;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeIdentifier;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeQueries;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level read API for self-service contact verification challenges (Issue #1416).
 *
 * <p>Exists so support can answer "the code never arrived": it shows where the code was sent,
 * whether the challenge is still valid, and how many attempts were made. Without it the
 * self-service flow is undiagnosable, since the feature owns its state privately.
 */
public interface OrgContactVerificationChallengeManagementApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put(
        "findList",
        new AdminPermissions(Set.of(DefaultAdminPermission.CONTACT_VERIFICATION_CHALLENGE_READ)));
    map.put(
        "get",
        new AdminPermissions(Set.of(DefaultAdminPermission.CONTACT_VERIFICATION_CHALLENGE_READ)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  ContactVerificationChallengeManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ContactVerificationChallengeQueries queries,
      RequestAttributes requestAttributes);

  ContactVerificationChallengeManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ContactVerificationChallengeIdentifier identifier,
      RequestAttributes requestAttributes);
}
