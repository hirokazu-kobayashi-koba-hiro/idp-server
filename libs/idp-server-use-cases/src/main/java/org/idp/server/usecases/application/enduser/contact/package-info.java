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
 * Sender adapters for {@code ContactVerificationCodeSender} (Issue #1416).
 *
 * <p>These are infrastructure sitting in the use-case module, which is not where they belong by
 * layering. They are here because the two natural homes are both closed:
 *
 * <ul>
 *   <li>{@code core-adapter} — reachable only through {@code
 *       ApplicationComponentContainerPluginLoader}, which runs before {@code EmailSenders} and
 *       {@code SmsSenders} exist (those need {@code HttpRequestExecutor}, which needs the container
 *       the loader is still building). A provider here could not resolve them, nor rebuild them.
 *   <li>a direct {@code new} from {@code IdpServerApplication} — {@code use-cases} does not depend
 *       on {@code core-adapter}, and that inversion is deliberate.
 * </ul>
 *
 * <p>Moving them to a module of their own is the open option, deferred until the feature is
 * complete.
 */
package org.idp.server.usecases.application.enduser.contact;
