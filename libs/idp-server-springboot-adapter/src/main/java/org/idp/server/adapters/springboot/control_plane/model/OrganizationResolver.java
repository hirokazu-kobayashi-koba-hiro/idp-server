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

package org.idp.server.adapters.springboot.control_plane.model;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.idp.server.platform.exception.UnauthorizedException;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;

/**
 * Utility class for resolving organization identifiers from HTTP requests.
 *
 * <p>This class provides methods to extract organization identifiers from various sources such as:
 * - URL path parameters (e.g., /organizations/{orgId}/...) - Request headers (X-Organization-Id) -
 * Query parameters
 */
public class OrganizationResolver {

  private static final Pattern ORG_PATH_PATTERN =
      Pattern.compile("^/v1/management/organizations/([^/]+)/");

  /**
   * Resolves organization identifier from HTTP request.
   *
   * <p>Resolution order: 1. URL path parameter (/organizations/{orgId}/...) 2. X-Organization-Id
   * header 3. organizationId query parameter
   *
   * @param request the HTTP request
   * @return the organization identifier
   * @throws UnauthorizedException if no organization identifier is found
   */
  public static OrganizationIdentifier resolve(HttpServletRequest request) {
    // 1. Try to extract from URL path
    String path = request.getRequestURI();
    Matcher matcher = ORG_PATH_PATTERN.matcher(path);
    if (matcher.find()) {
      String orgId = matcher.group(1);
      return new OrganizationIdentifier(orgId);
    }

    // 2. Try X-Organization-Id header
    String headerOrgId = request.getHeader("X-Organization-Id");
    if (headerOrgId != null && !headerOrgId.trim().isEmpty()) {
      return new OrganizationIdentifier(headerOrgId);
    }

    // 3. Try query parameter
    String queryOrgId = request.getParameter("organizationId");
    if (queryOrgId != null && !queryOrgId.trim().isEmpty()) {
      return new OrganizationIdentifier(queryOrgId);
    }

    throw new UnauthorizedException("Organization identifier not found in request");
  }

  /**
   * Checks if the request contains organization-level endpoints.
   *
   * @param request the HTTP request
   * @return true if the request is for organization-level management
   */
  public static boolean isOrganizationRequest(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.contains("/management/organizations/") || path.contains("/org-management/");
  }
}
