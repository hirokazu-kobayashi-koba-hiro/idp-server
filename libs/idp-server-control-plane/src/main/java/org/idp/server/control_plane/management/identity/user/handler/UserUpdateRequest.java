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

import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.core.openid.identity.UserIdentifier;

/**
 * Request object for user update operations.
 *
 * <p>Combines UserIdentifier (which user to update) with UserRegistrationRequest (what to update).
 * This separation allows update operations to reuse the same validation and context creation logic
 * as user registration.
 *
 * @param userIdentifier the identifier of the user to update
 * @param registrationRequest the updated user data (reuses registration request structure)
 */
public record UserUpdateRequest(
    UserIdentifier userIdentifier, UserRegistrationRequest registrationRequest) {}
