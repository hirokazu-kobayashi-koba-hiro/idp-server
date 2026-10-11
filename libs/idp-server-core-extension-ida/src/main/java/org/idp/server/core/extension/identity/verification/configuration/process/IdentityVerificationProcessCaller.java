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

package org.idp.server.core.extension.identity.verification.configuration.process;

/**
 * Who invokes a process, and therefore which endpoint may run it.
 *
 * <p>{@code end_user} processes run only from the end-user endpoint ({@code /me/...}), with the
 * user's access token. {@code external_service} processes run only from the callback endpoint
 * ({@code /internal/...}), where the external verification service authenticates itself.
 */
public enum IdentityVerificationProcessCaller {
  end_user,
  external_service
}
