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
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.control_plane.management.identity.user.io.UserUpdateRequest;
import org.idp.server.control_plane.management.identity.user.validator.UserOrganizationAssignmentsUpdateRequestValidator;
import org.idp.server.control_plane.management.identity.user.validator.UserRequestValidationResult;
import org.idp.server.control_plane.management.identity.user.verifier.UserRegistrationRelatedDataVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for updating user organization assignments.
 *
 * <p>Handles user organization assignments update logic following the Handler/Service pattern. This
 * operation is specific to organization-level APIs.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>User existence verification
 *   <li>Request validation
 *   <li>Context creation
 *   <li>User update in repository
 * </ul>
 *
 * <h2>NOT Responsibilities (handled by Handler/EntryService)</h2>
 *
 * <ul>
 *   <li>Permission checking
 *   <li>Organization access control
 *   <li>Audit logging
 *   <li>Transaction management
 * </ul>
 */
public class UserOrganizationAssignmentsUpdateService
    implements UserManagementService<UserUpdateRequest> {

  private final UserQueryRepository userQueryRepository;
  private final UserCommandRepository userCommandRepository;
  private final UserRegistrationRelatedDataVerifier relatedDataVerifier;

  public UserOrganizationAssignmentsUpdateService(
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      UserRegistrationRelatedDataVerifier relatedDataVerifier) {
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
    this.relatedDataVerifier = relatedDataVerifier;
  }

  public String type() {
    return "user_update_assignment_organization";
  }

  @Override
  public UserManagementResponse execute(
      UserManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      UserUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. User existence verification
    User before = userQueryRepository.get(tenant, request.userIdentifier());

    // 2. Validation
    UserOrganizationAssignmentsUpdateRequestValidator validator =
        new UserOrganizationAssignmentsUpdateRequestValidator(
            request.registrationRequest(), dryRun);
    UserRequestValidationResult validate = validator.validate();
    if (!validate.isValid()) {
      throw validate.toException();
    }
    relatedDataVerifier.verifyOrganizationAssignments(tenant, request.registrationRequest());

    // 3. Context creation
    User after = updateUser(request.registrationRequest(), before);

    // 4. Set before/after users to builder for context completion
    builder.withBefore(before);
    builder.withAfter(after);

    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    Map<String, Object> diff = JsonDiffCalculator.deepDiff(beforeJson, afterJson);
    Map<String, Object> contents = Map.of("result", after.toMap(), "diff", diff, "dry_run", dryRun);
    UserManagementResponse response = new UserManagementResponse(UserManagementStatus.OK, contents);

    // 5. Dry-run check
    if (dryRun) {
      return response;
    }

    // 6. Repository operation
    userCommandRepository.updateOrganizationAssignments(tenant, after);

    // 7. Re-fetch from DB to get complete user data
    User updatedUser = userQueryRepository.get(tenant, request.userIdentifier());

    // 8. Recalculate diff with actual DB state
    JsonNodeWrapper updatedJson = JsonNodeWrapper.fromMap(updatedUser.toMap());
    Map<String, Object> actualDiff = JsonDiffCalculator.deepDiff(beforeJson, updatedJson);
    Map<String, Object> actualContents =
        Map.of("result", updatedUser.toMap(), "diff", actualDiff, "dry_run", dryRun);

    return new UserManagementResponse(UserManagementStatus.OK, actualContents);
  }

  public User updateUser(UserRegistrationRequest request, User before) {
    // Create deep copy to avoid mutating 'before' object
    org.idp.server.platform.json.JsonConverter jsonConverter =
        org.idp.server.platform.json.JsonConverter.snakeCaseInstance();
    User updated = jsonConverter.read(jsonConverter.write(before), User.class);

    // Update assigned organizations if provided
    if (request.containsKey("assigned_organizations")) {
      updated.setAssignedOrganizations(request.assignedOrganizations());
    }

    // Update current organization if provided
    if (request.containsKey("current_organization_id") && request.currentOrganizationId() != null) {
      updated.setCurrentOrganizationId(
          new org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier(
              request.currentOrganizationId()));
    }

    return updated;
  }
}
