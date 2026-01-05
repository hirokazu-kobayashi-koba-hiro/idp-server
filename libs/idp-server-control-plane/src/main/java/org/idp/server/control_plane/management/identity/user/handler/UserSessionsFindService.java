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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.idp.server.control_plane.management.identity.user.UserManagementContextBuilder;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.control_plane.management.identity.user.io.UserSessionsFindRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.session.OPSessions;
import org.idp.server.core.openid.session.repository.OPSessionRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for finding user sessions.
 *
 * <p>Handles user session retrieval logic following the Handler/Service pattern. This is a
 * read-only operation that returns all OP sessions associated with a user.
 */
public class UserSessionsFindService implements UserManagementService<UserSessionsFindRequest> {

  private final OPSessionRepository opSessionRepository;

  public UserSessionsFindService(OPSessionRepository opSessionRepository) {
    this.opSessionRepository = opSessionRepository;
  }

  @Override
  public UserManagementResponse execute(
      UserManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      UserSessionsFindRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    UserIdentifier userIdentifier = request.userIdentifier();

    OPSessions sessions = opSessionRepository.findByUser(tenant, userIdentifier);

    List<Map<String, Object>> sessionMaps =
        sessions.stream().map(session -> session.toMap()).collect(Collectors.toList());

    return new UserManagementResponse(UserManagementStatus.OK, Map.of("list", sessionMaps));
  }
}
