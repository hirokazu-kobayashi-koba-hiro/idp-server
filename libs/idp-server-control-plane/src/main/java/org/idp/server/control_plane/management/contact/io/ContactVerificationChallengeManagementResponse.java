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

package org.idp.server.control_plane.management.contact.io;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallenge;

/** Management-side response for self-service contact verification challenges (Issue #1416). */
public class ContactVerificationChallengeManagementResponse {

  ContactVerificationChallengeManagementStatus status;
  Map<String, Object> contents;

  public ContactVerificationChallengeManagementResponse(
      ContactVerificationChallengeManagementStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public static ContactVerificationChallengeManagementResponse found(
      ContactVerificationChallenge challenge) {
    return new ContactVerificationChallengeManagementResponse(
        ContactVerificationChallengeManagementStatus.OK, challenge.toMap());
  }

  public static ContactVerificationChallengeManagementResponse list(
      List<ContactVerificationChallenge> challenges, long totalCount, int limit, int offset) {
    Map<String, Object> contents = new HashMap<>();
    contents.put("list", challenges.stream().map(ContactVerificationChallenge::toMap).toList());
    contents.put("total_count", totalCount);
    contents.put("limit", limit);
    contents.put("offset", offset);
    return new ContactVerificationChallengeManagementResponse(
        ContactVerificationChallengeManagementStatus.OK, contents);
  }

  public static ContactVerificationChallengeManagementResponse notFound() {
    Map<String, Object> contents = new HashMap<>();
    contents.put("error", "not_found");
    contents.put("error_description", "contact verification challenge not found.");
    return new ContactVerificationChallengeManagementResponse(
        ContactVerificationChallengeManagementStatus.NOT_FOUND, contents);
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, Object> contents() {
    return contents;
  }
}
