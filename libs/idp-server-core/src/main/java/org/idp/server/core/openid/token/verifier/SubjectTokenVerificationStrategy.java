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

package org.idp.server.core.openid.token.verifier;

import org.idp.server.core.openid.oauth.type.oauth.SecurityToken;
import org.idp.server.core.openid.oauth.type.oauth.SubjectTokenType;
import org.idp.server.core.openid.token.TokenRequestContext;
import org.idp.server.core.openid.token.service.SubjectTokenVerificationResult;

/**
 * SubjectTokenVerificationStrategy
 *
 * <p>Strategy interface for verifying subject_token in Token Exchange (RFC 8693). Each
 * implementation handles a specific token type identifier (RFC 8693 Section 3).
 *
 * <p>RFC 8693 Section 2.1:
 *
 * <blockquote>
 *
 * The authorization server MUST perform the appropriate validation procedures for the indicated
 * token type.
 *
 * </blockquote>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-3">RFC 8693 Section 3</a>
 */
public interface SubjectTokenVerificationStrategy {

  SubjectTokenType type();

  SubjectTokenVerificationResult verify(TokenRequestContext context, SecurityToken securityToken);
}
