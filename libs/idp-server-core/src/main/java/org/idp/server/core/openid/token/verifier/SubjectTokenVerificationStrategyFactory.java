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

import org.idp.server.platform.dependency.ApplicationComponentContainer;

/**
 * SubjectTokenVerificationStrategyFactory
 *
 * <p>SPI interface for creating {@link SubjectTokenVerificationStrategy} instances. Each
 * implementation provides a single strategy for a specific token type (RFC 8693 Section 3).
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} and registered
 * automatically. This allows external modules (e.g., SAML support) to add new token type handlers
 * without modifying core code.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-3">RFC 8693 Section 3</a>
 */
public interface SubjectTokenVerificationStrategyFactory {

  SubjectTokenVerificationStrategy create(ApplicationComponentContainer container);
}
