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

package org.idp.server.authenticators.webauthn4j.mds;

import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.metadata.data.MetadataBLOBPayloadEntry;
import com.webauthn4j.metadata.data.statement.MetadataStatement;
import com.webauthn4j.metadata.data.toc.AuthenticatorStatus;
import com.webauthn4j.metadata.data.toc.StatusReport;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.idp.server.platform.json.JsonReadable;

/**
 * Serializable cache entry for MDS metadata.
 *
 * <p>This class stores essential MDS metadata in a format that can be serialized to CacheStore.
 */
public class MdsCacheEntry implements JsonReadable {

  String aaguid;
  String description;
  String icon;
  String metadataStatementJson;
  List<StatusEntry> statusReports;

  public MdsCacheEntry() {
    this.statusReports = new ArrayList<>();
  }

  public MdsCacheEntry(
      String aaguid,
      String description,
      String icon,
      String metadataStatementJson,
      List<StatusEntry> statusReports) {
    this.aaguid = aaguid;
    this.description = description;
    this.icon = icon;
    this.metadataStatementJson = metadataStatementJson;
    this.statusReports = statusReports != null ? statusReports : new ArrayList<>();
  }

  public static MdsCacheEntry from(
      MetadataBLOBPayloadEntry entry, ObjectConverter objectConverter) {
    String aaguid = entry.getAaguid() != null ? entry.getAaguid().toString() : null;

    MetadataStatement statement = entry.getMetadataStatement();
    String description = null;
    String icon = null;
    String metadataStatementJson = null;

    if (statement != null) {
      description = statement.getDescription();
      icon = statement.getIcon();
      try {
        metadataStatementJson = objectConverter.getJsonConverter().writeValueAsString(statement);
      } catch (Exception e) {
        // Ignore serialization errors
      }
    }

    List<StatusEntry> statusEntries = new ArrayList<>();
    if (entry.getStatusReports() != null) {
      for (StatusReport report : entry.getStatusReports()) {
        statusEntries.add(StatusEntry.from(report));
      }
    }

    return new MdsCacheEntry(aaguid, description, icon, metadataStatementJson, statusEntries);
  }

  @SuppressWarnings("unchecked")
  public static MdsCacheEntry fromMap(Map<String, Object> map) {
    MdsCacheEntry entry = new MdsCacheEntry();
    entry.aaguid = (String) map.get("aaguid");
    entry.description = (String) map.get("description");
    entry.icon = (String) map.get("icon");
    entry.metadataStatementJson = (String) map.get("metadataStatementJson");

    List<Map<String, Object>> statusList = (List<Map<String, Object>>) map.get("statusReports");
    if (statusList != null) {
      entry.statusReports =
          statusList.stream().map(StatusEntry::fromMap).collect(Collectors.toList());
    }

    return entry;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("aaguid", aaguid);
    map.put("description", description);
    map.put("icon", icon);
    map.put("metadataStatementJson", metadataStatementJson);
    map.put(
        "statusReports",
        statusReports.stream().map(StatusEntry::toMap).collect(Collectors.toList()));
    return map;
  }

  public String aaguid() {
    return aaguid;
  }

  public String description() {
    return description;
  }

  public String icon() {
    return icon;
  }

  public Optional<MetadataStatement> getMetadataStatement(ObjectConverter objectConverter) {
    if (metadataStatementJson == null || metadataStatementJson.isEmpty()) {
      return Optional.empty();
    }
    try {
      MetadataStatement statement =
          objectConverter
              .getJsonConverter()
              .readValue(metadataStatementJson, MetadataStatement.class);
      return Optional.ofNullable(statement);
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  public org.idp.server.authenticators.webauthn4j.mds.AuthenticatorStatus toAuthenticatorStatus(
      String aaguidString) {
    if (statusReports == null || statusReports.isEmpty()) {
      return org.idp.server.authenticators.webauthn4j.mds.AuthenticatorStatus.notFound(
          aaguidString);
    }

    StatusEntry latestReport = statusReports.get(statusReports.size() - 1);
    List<String> statusHistory =
        statusReports.stream().map(StatusEntry::status).collect(Collectors.toList());

    AuthenticatorStatus webauthnStatus = null;
    try {
      if (latestReport.status() != null) {
        webauthnStatus = AuthenticatorStatus.valueOf(latestReport.status());
      }
    } catch (IllegalArgumentException e) {
      // Unknown status
    }

    LocalDate effectiveDate = latestReport.effectiveDate();

    return org.idp.server.authenticators.webauthn4j.mds.AuthenticatorStatus.of(
        aaguidString, webauthnStatus, effectiveDate, statusHistory);
  }

  /** Status report entry for caching. */
  public static class StatusEntry implements JsonReadable {
    String status;
    String effectiveDateStr;

    public StatusEntry() {}

    public StatusEntry(String status, String effectiveDateStr) {
      this.status = status;
      this.effectiveDateStr = effectiveDateStr;
    }

    public static StatusEntry from(StatusReport report) {
      String status = report.getStatus() != null ? report.getStatus().name() : "UNKNOWN";
      String effectiveDateStr =
          report.getEffectiveDate() != null ? report.getEffectiveDate().toString() : null;
      return new StatusEntry(status, effectiveDateStr);
    }

    public static StatusEntry fromMap(Map<String, Object> map) {
      return new StatusEntry((String) map.get("status"), (String) map.get("effectiveDateStr"));
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("status", status);
      map.put("effectiveDateStr", effectiveDateStr);
      return map;
    }

    public String status() {
      return status;
    }

    public LocalDate effectiveDate() {
      if (effectiveDateStr == null || effectiveDateStr.isEmpty()) {
        return null;
      }
      try {
        return LocalDate.parse(effectiveDateStr);
      } catch (Exception e) {
        return null;
      }
    }
  }
}
