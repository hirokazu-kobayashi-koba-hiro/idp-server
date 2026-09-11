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

package org.idp.server.control_plane.management.contact.handler;

import org.idp.server.control_plane.management.contact.ContactVerificationChallengeManagementContextBuilder;
import org.idp.server.control_plane.management.contact.io.ContactVerificationChallengeFindRequest;
import org.idp.server.control_plane.management.contact.io.ContactVerificationChallengeManagementResponse;
import org.idp.server.control_plane.management.contact.io.ContactVerificationChallengeManagementStatus;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallenge;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for finding a single contact verification challenge.
 *
 * <p>Handles contact verification challenge retrieval logic.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Query repository for contact verification challenge
 *   <li>Validate existence
 *   <li>Build response with interaction data
 * </ul>
 */
public class ContactVerificationChallengeFindService
    implements ContactVerificationChallengeManagementService<
        ContactVerificationChallengeFindRequest> {

  private final ContactVerificationChallengeRepository contactVerificationChallengeRepository;

  public ContactVerificationChallengeFindService(
      ContactVerificationChallengeRepository contactVerificationChallengeRepository) {
    this.contactVerificationChallengeRepository = contactVerificationChallengeRepository;
  }

  @Override
  public ContactVerificationChallengeManagementResponse execute(
      ContactVerificationChallengeManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      ContactVerificationChallengeFindRequest request,
      RequestAttributes requestAttributes) {

    ContactVerificationChallenge challenge =
        contactVerificationChallengeRepository.find(tenant, request.identifier());

    if (!challenge.exists()) {
      throw new ResourceNotFoundException(
          "Contact verification challenge not found: " + request.identifier().value());
    }

    // Update context builder with result (for audit logging)
    contextBuilder.withResult(challenge);

    return new ContactVerificationChallengeManagementResponse(
        ContactVerificationChallengeManagementStatus.OK, challenge.toMap());
  }
}
