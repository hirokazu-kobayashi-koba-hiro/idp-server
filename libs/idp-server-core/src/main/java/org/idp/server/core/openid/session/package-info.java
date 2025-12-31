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

/**
 * OIDC Session Management package.
 *
 * <p>This package provides session management functionality for OpenID Connect logout
 * specifications:
 *
 * <ul>
 *   <li>RP-Initiated Logout
 *   <li>Back-Channel Logout
 *   <li>Front-Channel Logout
 *   <li>Session Management (optional)
 * </ul>
 *
 * <p>The session model consists of:
 *
 * <ul>
 *   <li>OPSession - Browser-OP session identified by cookie
 *   <li>ClientSession - Per-RP session identified by sid claim in ID Token
 * </ul>
 */
package org.idp.server.core.openid.session;
