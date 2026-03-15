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
package org.idp.server.core.openid.authentication.risk.geolocation;

public class GeoLocation {

  double latitude;
  double longitude;
  String country;
  String city;

  public GeoLocation() {
    this.latitude = 0.0;
    this.longitude = 0.0;
    this.country = "";
    this.city = "";
  }

  public GeoLocation(double latitude, double longitude, String country, String city) {
    this.latitude = latitude;
    this.longitude = longitude;
    this.country = country;
    this.city = city;
  }

  public double latitude() {
    return latitude;
  }

  public double longitude() {
    return longitude;
  }

  public String country() {
    return country;
  }

  public String city() {
    return city;
  }

  public boolean exists() {
    return latitude != 0.0 || longitude != 0.0;
  }

  public static GeoLocation empty() {
    return new GeoLocation();
  }
}
