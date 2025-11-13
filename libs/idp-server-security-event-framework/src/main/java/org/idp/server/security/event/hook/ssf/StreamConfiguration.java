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

package org.idp.server.security.event.hook.ssf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

/**
 * Stream Configuration for OpenID Shared Signals Framework (SSF).
 *
 * <p>Represents the configuration of an event stream between a Transmitter and Receiver. Based on
 * OpenID Shared Signals and Events Framework Specification 1.0.
 *
 * @see <a href="https://openid.net/specs/openid-sse-framework-1_0.html#stream-config">Stream
 *     Configuration</a>
 */
public class StreamConfiguration implements JsonReadable {

  /**
   * Read-only: Issuer identifier (HTTPS URL). Must match the iss claim in SETs.
   *
   * <p>Inherited from metadata, not directly configurable in stream.
   */
  String iss;

  /**
   * Read-only: Audience claim identifying the Event Receiver(s).
   *
   * <p>Typically the OAuth 2.0 client ID. Can be a string or array of strings for multiple
   * receivers representing the same entity.
   */
  List<String> aud = new ArrayList<>();

  /**
   * Read-only: Event types supported by the Transmitter for this Receiver.
   *
   * <p>Array of URIs identifying supported event types.
   */
  List<String> eventsSupported = new ArrayList<>();

  /**
   * Read-write: Event types requested by the Receiver.
   *
   * <p>Array of URIs identifying the events the Receiver wants to receive.
   */
  List<String> eventsRequested = new ArrayList<>();

  /**
   * Read-only: Event types actually delivered.
   *
   * <p>Intersection of events_supported and events_requested.
   */
  List<String> eventsDelivered = new ArrayList<>();

  /**
   * Read-write: Delivery method configuration.
   *
   * <p>Contains delivery-specific parameters (e.g., URL for push, polling interval).
   */
  Map<String, Object> delivery = new HashMap<>();

  /**
   * Read-write: Subject identifier format.
   *
   * <p>Format that the Receiver wants for event subjects (e.g., "iss_sub", "email").
   */
  String format;

  /**
   * Read-only: Minimum verification interval in seconds.
   *
   * <p>Minimum time between verification requests.
   */
  Integer minVerificationInterval;

  public StreamConfiguration() {}

  public String iss() {
    return iss;
  }

  public List<String> aud() {
    if (aud == null) {
      return new ArrayList<>();
    }
    return aud;
  }

  /**
   * Returns the first audience value.
   *
   * @return the first aud value, or null if empty
   */
  public String audValue() {
    if (aud == null || aud.isEmpty()) {
      return null;
    }
    return aud.getFirst();
  }

  public List<String> eventsSupported() {
    if (eventsSupported == null) {
      return new ArrayList<>();
    }
    return eventsSupported;
  }

  public List<String> eventsRequested() {
    if (eventsRequested == null) {
      return new ArrayList<>();
    }
    return eventsRequested;
  }

  public List<String> eventsDelivered() {
    if (eventsDelivered == null) {
      return new ArrayList<>();
    }
    return eventsDelivered;
  }

  public Map<String, Object> delivery() {
    if (delivery == null) {
      return new HashMap<>();
    }
    return delivery;
  }

  public String format() {
    return format;
  }

  public Integer minVerificationInterval() {
    return minVerificationInterval;
  }

  public boolean hasAud() {
    return aud != null && !aud.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (iss != null) {
      map.put("iss", iss);
    }
    if (aud != null && !aud.isEmpty()) {
      map.put("aud", aud.size() == 1 ? aud.get(0) : aud);
    }
    if (eventsSupported != null && !eventsSupported.isEmpty()) {
      map.put("events_supported", eventsSupported);
    }
    if (eventsRequested != null && !eventsRequested.isEmpty()) {
      map.put("events_requested", eventsRequested);
    }
    if (eventsDelivered != null && !eventsDelivered.isEmpty()) {
      map.put("events_delivered", eventsDelivered);
    }
    if (delivery != null && !delivery.isEmpty()) {
      map.put("delivery", delivery);
    }
    if (format != null) {
      map.put("format", format);
    }
    if (minVerificationInterval != null) {
      map.put("min_verification_interval", minVerificationInterval);
    }
    return map;
  }
}
