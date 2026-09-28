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

package org.idp.server.core.openid.oauth.clientauthenticator.plugin;

import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.platform.dependency.ApplicationComponentContainer;

/**
 * Factory SPI for {@link ClientAuthenticator} implementations that need dependencies.
 *
 * <p>{@link ClientAuthenticator} itself is loaded through a plain {@code ServiceLoader}, so
 * implementations cannot receive repositories. Authenticators that must query application state
 * (e.g. Attestation-Based Client Authentication resolving a registered Client Instance Key)
 * register a factory instead and receive the {@link ApplicationComponentContainer}.
 *
 * <p>Modeled after {@code AuthenticationDeviceNotifierFactory}. Factories take precedence over
 * plain SPI registrations of the same {@link ClientAuthenticationType}.
 */
public interface ClientAuthenticatorFactory {

  ClientAuthenticationType type();

  ClientAuthenticator create(ApplicationComponentContainer container);
}
