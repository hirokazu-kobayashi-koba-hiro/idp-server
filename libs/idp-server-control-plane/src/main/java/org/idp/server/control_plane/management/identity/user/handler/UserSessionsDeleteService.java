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

package org.idp.server.control_plane.management.identity.user.handler;

import java.util.Map;
import org.idp.server.control_plane.management.identity.user.UserManagementContextBuilder;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.control_plane.management.identity.user.io.UserSessionsDeleteRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.session.repository.OPSessionRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for deleting all user sessions.
 *
 * <p>Handles bulk deletion of all OP sessions for a user following the Handler/Service pattern.
 */
public class UserSessionsDeleteService implements UserManagementService<UserSessionsDeleteRequest> {

  private final OPSessionRepository opSessionRepository;

  public UserSessionsDeleteService(OPSessionRepository opSessionRepository) {
    this.opSessionRepository = opSessionRepository;
  }

  @Override
  public UserManagementResponse execute(
      UserManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      UserSessionsDeleteRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    if (dryRun) {
      return new UserManagementResponse(
          UserManagementStatus.OK,
          Map.of(
              "message",
              "All sessions would be deleted",
              "user_id",
              request.userIdentifier().value()));
    }

    opSessionRepository.deleteByUser(tenant, request.userIdentifier());

    return new UserManagementResponse(UserManagementStatus.NO_CONTENT, Map.of());
  }
}
