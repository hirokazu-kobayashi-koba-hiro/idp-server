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

package org.idp.server.adapters.springboot.organization;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.idp.server.adapters.springboot.control_plane.model.OrganizationResolver;
import org.idp.server.platform.exception.UnauthorizedException;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationResolverTest {

  @Mock private HttpServletRequest request;

  @Test
  void resolve_FromUrlPath_ReturnsOrganizationId() {
    // Given
    when(request.getRequestURI()).thenReturn("/organizations/org-123/tenants");

    // When
    OrganizationIdentifier result = OrganizationResolver.resolve(request);

    // Then
    assertEquals("org-123", result.value());
  }

  @Test
  void resolve_FromUrlPathWithSubpath_ReturnsOrganizationId() {
    // Given
    when(request.getRequestURI()).thenReturn("/organizations/my-org-456/tenants/tenant-789");

    // When
    OrganizationIdentifier result = OrganizationResolver.resolve(request);

    // Then
    assertEquals("my-org-456", result.value());
  }

  @Test
  void resolve_FromHeader_WhenNoUrlPath_ReturnsOrganizationId() {
    // Given
    when(request.getRequestURI()).thenReturn("/org-management/users");
    when(request.getHeader("X-Organization-Id")).thenReturn("header-org-789");

    // When
    OrganizationIdentifier result = OrganizationResolver.resolve(request);

    // Then
    assertEquals("header-org-789", result.value());
  }

  @Test
  void resolve_FromQueryParameter_WhenNoUrlPathOrHeader_ReturnsOrganizationId() {
    // Given
    when(request.getRequestURI()).thenReturn("/org-management/users");
    when(request.getHeader("X-Organization-Id")).thenReturn(null);
    when(request.getParameter("organizationId")).thenReturn("query-org-999");

    // When
    OrganizationIdentifier result = OrganizationResolver.resolve(request);

    // Then
    assertEquals("query-org-999", result.value());
  }

  @Test
  void resolve_PrioritizesUrlPathOverHeader() {
    // Given
    when(request.getRequestURI()).thenReturn("/organizations/path-org-111/tenants");

    // When
    OrganizationIdentifier result = OrganizationResolver.resolve(request);

    // Then
    assertEquals("path-org-111", result.value());
  }

  @Test
  void resolve_PrioritizesHeaderOverQueryParameter() {
    // Given
    when(request.getRequestURI()).thenReturn("/org-management/users");
    when(request.getHeader("X-Organization-Id")).thenReturn("header-org-333");

    // When
    OrganizationIdentifier result = OrganizationResolver.resolve(request);

    // Then
    assertEquals("header-org-333", result.value());
  }

  @Test
  void resolve_ThrowsException_WhenNoOrganizationIdFound() {
    // Given
    when(request.getRequestURI()).thenReturn("/some/other/path");
    when(request.getHeader("X-Organization-Id")).thenReturn(null);
    when(request.getParameter("organizationId")).thenReturn(null);

    // When & Then
    UnauthorizedException exception =
        assertThrows(UnauthorizedException.class, () -> OrganizationResolver.resolve(request));
    assertEquals("Organization identifier not found in request", exception.getMessage());
  }

  @Test
  void resolve_ThrowsException_WhenEmptyHeader() {
    // Given
    when(request.getRequestURI()).thenReturn("/some/other/path");
    when(request.getHeader("X-Organization-Id")).thenReturn("  ");
    when(request.getParameter("organizationId")).thenReturn(null);

    // When & Then
    UnauthorizedException exception =
        assertThrows(UnauthorizedException.class, () -> OrganizationResolver.resolve(request));
    assertEquals("Organization identifier not found in request", exception.getMessage());
  }

  @Test
  void resolve_ThrowsException_WhenEmptyQueryParameter() {
    // Given
    when(request.getRequestURI()).thenReturn("/some/other/path");
    when(request.getHeader("X-Organization-Id")).thenReturn(null);
    when(request.getParameter("organizationId")).thenReturn("");

    // When & Then
    UnauthorizedException exception =
        assertThrows(UnauthorizedException.class, () -> OrganizationResolver.resolve(request));
    assertEquals("Organization identifier not found in request", exception.getMessage());
  }

  @Test
  void isOrganizationRequest_ReturnsTrue_WhenOrganizationsPath() {
    // Given
    when(request.getRequestURI()).thenReturn("/organizations/org-123/tenants");

    // When
    boolean result = OrganizationResolver.isOrganizationRequest(request);

    // Then
    assertTrue(result);
  }

  @Test
  void isOrganizationRequest_ReturnsTrue_WhenOrgManagementPath() {
    // Given
    when(request.getRequestURI()).thenReturn("/org-management/users");

    // When
    boolean result = OrganizationResolver.isOrganizationRequest(request);

    // Then
    assertTrue(result);
  }

  @Test
  void isOrganizationRequest_ReturnsFalse_WhenRegularManagementPath() {
    // Given
    when(request.getRequestURI()).thenReturn("/management/users");

    // When
    boolean result = OrganizationResolver.isOrganizationRequest(request);

    // Then
    assertFalse(result);
  }

  @Test
  void isOrganizationRequest_ReturnsFalse_WhenNonManagementPath() {
    // Given
    when(request.getRequestURI()).thenReturn("/api/v1/users");

    // When
    boolean result = OrganizationResolver.isOrganizationRequest(request);

    // Then
    assertFalse(result);
  }
}
