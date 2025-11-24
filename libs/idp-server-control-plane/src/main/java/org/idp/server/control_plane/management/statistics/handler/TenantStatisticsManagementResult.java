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

package org.idp.server.control_plane.management.statistics.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.statistics.TenantStatisticsResponse;
import org.idp.server.control_plane.management.statistics.TenantStatisticsStatus;

public class TenantStatisticsManagementResult {

  private final AuditableContext context;
  private final TenantStatisticsResponse response;
  private final ManagementApiException exception;

  private TenantStatisticsManagementResult(
      AuditableContext context,
      TenantStatisticsResponse response,
      ManagementApiException exception) {
    this.context = context;
    this.response = response;
    this.exception = exception;
  }

  public static TenantStatisticsManagementResult success(
      AuditableContext context, TenantStatisticsResponse response) {
    return new TenantStatisticsManagementResult(context, response, null);
  }

  public static TenantStatisticsManagementResult error(
      AuditableContext context, ManagementApiException exception) {
    return new TenantStatisticsManagementResult(context, null, exception);
  }

  public boolean hasException() {
    return exception != null;
  }

  public AuditableContext context() {
    return context;
  }

  public TenantStatisticsResponse toResponse() {
    if (hasException()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());

      TenantStatisticsStatus status = mapExceptionToStatus(exception);
      return TenantStatisticsResponse.error(status, errorResponse);
    }
    return response;
  }

  private TenantStatisticsStatus mapExceptionToStatus(ManagementApiException exception) {
    if (exception instanceof InvalidRequestException) {
      return TenantStatisticsStatus.INVALID_REQUEST;
    } else if (exception instanceof PermissionDeniedException) {
      return TenantStatisticsStatus.FORBIDDEN;
    } else if (exception instanceof ResourceNotFoundException) {
      return TenantStatisticsStatus.NOT_FOUND;
    }
    return TenantStatisticsStatus.SERVER_ERROR;
  }

  public TenantStatisticsResponse response() {
    return response;
  }

  public ManagementApiException exception() {
    return exception;
  }
}
