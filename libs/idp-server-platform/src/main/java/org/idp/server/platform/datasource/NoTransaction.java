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

package org.idp.server.platform.datasource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an EntryService method so the proxy does not wrap it in a single transaction.
 *
 * <p>The method is expected to manage transaction boundaries explicitly using {@link Transactions}.
 * Typical use: flows that contain an external HTTP call between a load step and a write step, where
 * holding ACCESS SHARE / RowExclusiveLock for the duration of the remote call would block DDL,
 * starve the connection pool, and impede autovacuum.
 *
 * <p>The proxy still resolves tenant context, sets up logging MDC, and applies RLS — it just
 * declines to open a transaction itself. Inside the method, use {@code Transactions.readOnly(...)}
 * and {@code Transactions.write(...)} to delimit short transactions around the external call.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NoTransaction {}
