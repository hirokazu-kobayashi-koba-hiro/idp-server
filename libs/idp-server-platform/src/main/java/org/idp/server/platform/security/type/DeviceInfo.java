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

package org.idp.server.platform.security.type;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Device information extracted from User-Agent header.
 *
 * <p>Provides device identification for authentication device management. Parses User-Agent string
 * to extract device type, OS, browser, and version information.
 */
public class DeviceInfo {

  private final String device;
  private final String os;
  private final String osVersion;
  private final String browser;
  private final String browserVersion;
  private final boolean mobile;

  public DeviceInfo(
      String device,
      String os,
      String osVersion,
      String browser,
      String browserVersion,
      boolean mobile) {
    this.device = device;
    this.os = os;
    this.osVersion = osVersion;
    this.browser = browser;
    this.browserVersion = browserVersion;
    this.mobile = mobile;
  }

  /**
   * Parses User-Agent string to extract device information.
   *
   * @param userAgent the User-Agent header value
   * @return DeviceInfo with extracted information
   */
  public static DeviceInfo parse(String userAgent) {
    if (userAgent == null || userAgent.isEmpty()) {
      return unknown();
    }

    String ua = userAgent.toLowerCase();
    String device = parseDevice(ua);
    String os = parseOs(ua);
    String osVersion = parseOsVersion(userAgent);
    String browser = parseBrowser(ua);
    String browserVersion = parseBrowserVersion(userAgent);
    boolean mobile = isMobile(ua);

    return new DeviceInfo(device, os, osVersion, browser, browserVersion, mobile);
  }

  private static String parseDevice(String ua) {
    if (ua.contains("iphone")) {
      return "iPhone";
    }
    if (ua.contains("ipad")) {
      return "iPad";
    }
    if (ua.contains("android")) {
      if (ua.contains("mobile")) {
        return "Android Phone";
      }
      return "Android Tablet";
    }
    if (ua.contains("macintosh") || ua.contains("mac os")) {
      return "Mac";
    }
    if (ua.contains("windows")) {
      return "Windows PC";
    }
    if (ua.contains("linux")) {
      return "Linux";
    }
    return "Unknown";
  }

  private static String parseOs(String ua) {
    if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) {
      return "iOS";
    }
    if (ua.contains("android")) {
      return "Android";
    }
    if (ua.contains("macintosh") || ua.contains("mac os")) {
      return "macOS";
    }
    if (ua.contains("windows")) {
      return "Windows";
    }
    if (ua.contains("linux")) {
      return "Linux";
    }
    return "Unknown";
  }

  /**
   * Extracts OS version from User-Agent.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>Mac OS X 10_15_7 → "10.15.7"
   *   <li>iPhone OS 17_2_1 → "17.2.1"
   *   <li>Android 14 → "14"
   *   <li>Windows NT 10.0 → "10"
   * </ul>
   */
  private static String parseOsVersion(String userAgent) {
    // iOS: "iPhone OS 17_2_1" or "CPU iPhone OS 17_2_1"
    Pattern iosPattern = Pattern.compile("(?:iPhone|CPU) OS (\\d+[_.]\\d+(?:[_.]\\d+)?)");
    Matcher iosMatcher = iosPattern.matcher(userAgent);
    if (iosMatcher.find()) {
      return iosMatcher.group(1).replace("_", ".");
    }

    // macOS: "Mac OS X 10_15_7" or "Mac OS X 10.15.7"
    Pattern macPattern = Pattern.compile("Mac OS X (\\d+[_.]\\d+(?:[_.]\\d+)?)");
    Matcher macMatcher = macPattern.matcher(userAgent);
    if (macMatcher.find()) {
      return macMatcher.group(1).replace("_", ".");
    }

    // Android: "Android 14" or "Android 14.0"
    Pattern androidPattern = Pattern.compile("Android (\\d+(?:\\.\\d+)?)");
    Matcher androidMatcher = androidPattern.matcher(userAgent);
    if (androidMatcher.find()) {
      return androidMatcher.group(1);
    }

    // Windows: "Windows NT 10.0"
    Pattern windowsPattern = Pattern.compile("Windows NT (\\d+\\.\\d+)");
    Matcher windowsMatcher = windowsPattern.matcher(userAgent);
    if (windowsMatcher.find()) {
      String ntVersion = windowsMatcher.group(1);
      // Map NT version to Windows version
      return mapWindowsVersion(ntVersion);
    }

    return "";
  }

  private static String mapWindowsVersion(String ntVersion) {
    switch (ntVersion) {
      case "10.0":
        return "10/11"; // Can't distinguish Win10 from Win11
      case "6.3":
        return "8.1";
      case "6.2":
        return "8";
      case "6.1":
        return "7";
      default:
        return ntVersion;
    }
  }

  private static String parseBrowser(String ua) {
    // Order matters: Edge contains "chrome", Safari check needs to exclude Chrome
    if (ua.contains("edg/") || ua.contains("edge/")) {
      return "Edge";
    }
    if (ua.contains("firefox")) {
      return "Firefox";
    }
    if (ua.contains("chrome") && !ua.contains("edg")) {
      return "Chrome";
    }
    if (ua.contains("safari") && !ua.contains("chrome") && !ua.contains("chromium")) {
      return "Safari";
    }
    if (ua.contains("opera") || ua.contains("opr/")) {
      return "Opera";
    }
    return "Unknown";
  }

  /**
   * Extracts browser version from User-Agent.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>Chrome/120.0.0.0 → "120"
   *   <li>Safari/604.1 Version/17.2 → "17.2"
   *   <li>Firefox/121.0 → "121"
   *   <li>Edg/120.0.0.0 → "120"
   * </ul>
   */
  private static String parseBrowserVersion(String userAgent) {
    String ua = userAgent.toLowerCase();

    // Edge: "Edg/120.0.0.0"
    if (ua.contains("edg/")) {
      Pattern pattern = Pattern.compile("Edg/(\\d+)", Pattern.CASE_INSENSITIVE);
      Matcher matcher = pattern.matcher(userAgent);
      if (matcher.find()) {
        return matcher.group(1);
      }
    }

    // Firefox: "Firefox/121.0"
    if (ua.contains("firefox")) {
      Pattern pattern = Pattern.compile("Firefox/(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
      Matcher matcher = pattern.matcher(userAgent);
      if (matcher.find()) {
        return matcher.group(1);
      }
    }

    // Chrome (but not Edge): "Chrome/120.0.0.0"
    if (ua.contains("chrome") && !ua.contains("edg")) {
      Pattern pattern = Pattern.compile("Chrome/(\\d+)", Pattern.CASE_INSENSITIVE);
      Matcher matcher = pattern.matcher(userAgent);
      if (matcher.find()) {
        return matcher.group(1);
      }
    }

    // Safari: "Version/17.2" (Safari uses Version/ for version number)
    if (ua.contains("safari") && !ua.contains("chrome")) {
      Pattern pattern = Pattern.compile("Version/(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
      Matcher matcher = pattern.matcher(userAgent);
      if (matcher.find()) {
        return matcher.group(1);
      }
    }

    // Opera: "OPR/106.0"
    if (ua.contains("opr/")) {
      Pattern pattern = Pattern.compile("OPR/(\\d+)", Pattern.CASE_INSENSITIVE);
      Matcher matcher = pattern.matcher(userAgent);
      if (matcher.find()) {
        return matcher.group(1);
      }
    }

    return "";
  }

  private static boolean isMobile(String ua) {
    return ua.contains("mobile")
        || ua.contains("android")
        || ua.contains("iphone")
        || ua.contains("ipad")
        || ua.contains("ipod");
  }

  public static DeviceInfo unknown() {
    return new DeviceInfo("Unknown", "Unknown", "", "Unknown", "", false);
  }

  public String device() {
    return device;
  }

  public String os() {
    return os;
  }

  public String osVersion() {
    return osVersion;
  }

  public boolean hasOsVersion() {
    return osVersion != null && !osVersion.isEmpty();
  }

  public String browser() {
    return browser;
  }

  public String browserVersion() {
    return browserVersion;
  }

  public boolean hasBrowserVersion() {
    return browserVersion != null && !browserVersion.isEmpty();
  }

  public boolean isMobile() {
    return mobile;
  }

  public String platform() {
    return mobile ? "Mobile" : "Desktop";
  }

  /**
   * Returns OS with version if available.
   *
   * <p>Examples: "iOS 17.2", "macOS 10.15", "Windows 10/11"
   *
   * @return OS with version
   */
  public String osWithVersion() {
    if (hasOsVersion()) {
      return os + " " + osVersion;
    }
    return os;
  }

  /**
   * Returns browser with version if available.
   *
   * <p>Examples: "Safari 17.2", "Chrome 120"
   *
   * @return browser with version
   */
  public String browserWithVersion() {
    if (hasBrowserVersion()) {
      return browser + " " + browserVersion;
    }
    return browser;
  }

  /**
   * Returns a human-readable label for the device.
   *
   * <p>Format: "{device} - {browser} ({os} {version})"
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>"iPhone - Safari (iOS 17.2)"
   *   <li>"Mac - Chrome (macOS 10.15)"
   *   <li>"Windows PC - Edge (Windows 10/11)"
   * </ul>
   *
   * @return device label
   */
  public String toLabel() {
    StringBuilder label = new StringBuilder();
    label.append(device).append(" - ").append(browser);
    if (hasOsVersion()) {
      label.append(" (").append(os).append(" ").append(osVersion).append(")");
    }
    return label.toString();
  }

  /**
   * Returns the model information.
   *
   * <p>For passkey management, browser with version is used as model to help users identify the
   * registration context.
   *
   * @return browser with version as model
   */
  public String model() {
    return browserWithVersion();
  }

  @Override
  public String toString() {
    return "DeviceInfo{"
        + "device='"
        + device
        + '\''
        + ", os='"
        + os
        + '\''
        + ", osVersion='"
        + osVersion
        + '\''
        + ", browser='"
        + browser
        + '\''
        + ", browserVersion='"
        + browserVersion
        + '\''
        + ", mobile="
        + mobile
        + '}';
  }
}
