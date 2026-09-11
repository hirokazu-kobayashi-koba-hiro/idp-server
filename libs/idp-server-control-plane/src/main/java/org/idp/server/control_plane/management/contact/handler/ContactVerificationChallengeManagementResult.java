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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.contact.io.ContactVerificationChallengeManagementResponse;
import org.idp.server.control_plane.management.contact.io.ContactVerificationChallengeManagementStatus;
import org.idp.server.control_plane.management.exception.*;

/**
 * Result object for contact verification challenge management operations.
 *
 * <p>Encapsulates the outcome of contact verification challenge management Handler/Service pattern
 * operations. Follows the Result-Exception Hybrid pattern:
 *
 * <ul>
 *   <li>Service layer throws ManagementApiException on validation/verification failures
 *   <li>Handler layer catches exceptions and converts to Result
 *   <li>EntryService layer converts Result to HTTP response
 * </ul>
 *
 * @see ContactVerificationChallengeManagementHandler
 * @see ContactVerificationChallengeManagementService
 */
public class ContactVerificationChallengeManagementResult {

  private AuditableContext context;
  private ManagementApiException exception;
  private ContactVerificationChallengeManagementResponse response;

  private ContactVerificationChallengeManagementResult(
      AuditableContext context,
      ManagementApiException exception,
      ContactVerificationChallengeManagementResponse response) {
    this.context = context;
    this.exception = exception;
    this.response = response;
  }

  /**
   * Creates a successful result with context.
   *
   * @param context the operation context
   * @param response the success response
   * @return successful result with context
   */
  public static ContactVerificationChallengeManagementResult success(
      AuditableContext context, ContactVerificationChallengeManagementResponse response) {
    return new ContactVerificationChallengeManagementResult(context, null, response);
  }

  /**
   * Creates an error result from an exception.
   *
   * @param context the operation context (may be partial)
   * @param exception the exception that occurred
   * @return error result
   */
  public static ContactVerificationChallengeManagementResult error(
      AuditableContext context, ManagementApiException exception) {
    return new ContactVerificationChallengeManagementResult(context, exception, null);
  }

  private static ContactVerificationChallengeManagementStatus mapExceptionToStatus(
      ManagementApiException exception) {
    if (exception instanceof ResourceNotFoundException) {
      return ContactVerificationChallengeManagementStatus.NOT_FOUND;
    }
    if (exception instanceof PermissionDeniedException
        || exception instanceof OrganizationAccessDeniedException) {
      return ContactVerificationChallengeManagementStatus.FORBIDDEN;
    }
    return ContactVerificationChallengeManagementStatus.INVALID_REQUEST;
  }

  public AuditableContext context() {
    return context;
  }

  public boolean hasException() {
    return exception != null;
  }

  public ManagementApiException getException() {
    return exception;
  }

  public ContactVerificationChallengeManagementResponse toResponse() {
    if (hasException()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());
      ContactVerificationChallengeManagementStatus status = mapExceptionToStatus(exception);
      return new ContactVerificationChallengeManagementResponse(status, errorResponse);
    }
    return response;
  }
}
