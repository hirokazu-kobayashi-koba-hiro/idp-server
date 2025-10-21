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

package org.idp.server.control_plane.management.permission.handler;

import org.idp.server.control_plane.management.permission.io.PermissionRequest;
import org.idp.server.core.openid.identity.permission.PermissionIdentifier;

/**
 * Request wrapper for permission update operations.
 *
 * <p>Combines permission identifier with update request payload.
 */
public record PermissionUpdateRequest(
    PermissionIdentifier identifier, PermissionRequest permissionRequest) {}
