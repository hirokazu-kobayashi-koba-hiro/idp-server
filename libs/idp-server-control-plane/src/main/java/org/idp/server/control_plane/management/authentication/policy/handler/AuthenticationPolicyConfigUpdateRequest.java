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

package org.idp.server.control_plane.management.authentication.policy.handler;

import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigRequest;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfigurationIdentifier;

/**
 * Request wrapper for update operation.
 *
 * @param identifier the configuration identifier to update
 * @param request the update request
 */
public record AuthenticationPolicyConfigUpdateRequest(
    AuthenticationPolicyConfigurationIdentifier identifier,
    AuthenticationPolicyConfigRequest request) {}
