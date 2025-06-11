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


package org.idp.server.platform.plugin;

import org.idp.server.platform.audit.AuditLogWriter;
import org.idp.server.platform.audit.AuditLogWriterProvider;
import org.idp.server.platform.audit.AuditLogWriters;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.log.LoggerWrapper;

import java.util.ArrayList;
import java.util.List;

public class AuditLogWriterPluginLoader extends PluginLoader{

    private static final LoggerWrapper log = LoggerWrapper.getLogger(EmailSenderPluginLoader.class);

    public static AuditLogWriters load(ApplicationComponentContainer container) {

        List<AuditLogWriter> writers = new ArrayList<>();
        List<AuditLogWriterProvider> internalAuditLogWriterProviders = loadFromInternalModule(AuditLogWriterProvider.class);
        for (AuditLogWriterProvider provider : internalAuditLogWriterProviders) {
            AuditLogWriter auditLogWriter = provider.provide(container);
            writers.add(auditLogWriter);
            log.info("Dynamic Registered internal AuditLogWriter: " + auditLogWriter.getClass().getSimpleName());
        }

        List<AuditLogWriterProvider> externalAuditLogWriterProviders = loadFromExternalModule(AuditLogWriterProvider.class);
        for (AuditLogWriterProvider provider : externalAuditLogWriterProviders) {
            AuditLogWriter auditLogWriter = provider.provide(container);
            writers.add(auditLogWriter);
            log.info("Dynamic Registered external AuditLogWriter: " + auditLogWriter.getClass().getSimpleName());
        }

        return new AuditLogWriters(writers);
    }


}
