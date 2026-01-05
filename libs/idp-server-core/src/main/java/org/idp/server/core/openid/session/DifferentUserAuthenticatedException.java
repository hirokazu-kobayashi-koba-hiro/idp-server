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

package org.idp.server.core.openid.session;

import org.idp.server.platform.exception.ForbiddenException;

/**
 * DifferentUserAuthenticatedException
 *
 * <p>Thrown when a user attempts to authenticate as a different user while another user's session
 * is active and the session switch policy is set to STRICT.
 *
 * <p>This exception prevents session hijacking and ensures session integrity in strict security
 * environments. The user must explicitly logout before switching to a different account.
 */
public class DifferentUserAuthenticatedException extends ForbiddenException {

  private final String existingUserSub;
  private final String authenticatedUserSub;

  public DifferentUserAuthenticatedException(String existingUserSub, String authenticatedUserSub) {
    super(
        String.format(
            "Different user authenticated. Existing session user: %s, Authenticated user: %s. "
                + "Please logout first before switching accounts.",
            existingUserSub, authenticatedUserSub));
    this.existingUserSub = existingUserSub;
    this.authenticatedUserSub = authenticatedUserSub;
  }

  public String existingUserSub() {
    return existingUserSub;
  }

  public String authenticatedUserSub() {
    return authenticatedUserSub;
  }
}
