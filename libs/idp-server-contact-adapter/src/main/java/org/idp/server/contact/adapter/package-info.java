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
 * Adapters that drive the self-service contact code exchange (Issue #1416).
 *
 * <p>These implement {@code ContactVerificationGateway} and {@code ContactChangeNotifier} on top of
 * the tenant's existing {@code type: "email"} / {@code type: "sms"} authentication configuration,
 * so a tenant that already runs login OTP needs no configuration of its own.
 *
 * <h2>Why a module rather than a package in core</h2>
 *
 * The configuration types they read ({@code EmailAuthenticationConfiguration}, {@code
 * SmsAuthenticationConfiguration}) live in {@code idp-server-authentication-interactors}, which
 * depends on {@code idp-server-core} rather than the other way round — so core cannot see them.
 *
 * <p>{@code idp-server-core-adapter} is also closed: these are constructed by {@code
 * IdpServerApplication} in {@code idp-server-use-cases}, which deliberately does not depend on
 * core-adapter, and they cannot be produced by the component plugin loader either — that runs
 * before {@code EmailSenders} / {@code SmsSenders} exist.
 *
 * <p>They sat in {@code use-cases} while the feature was being built, which put infrastructure in
 * the use-case layer. This module is where they belong: above authentication-interactors, visible
 * to use-cases, with no use-case logic of its own.
 */
package org.idp.server.contact.adapter;
