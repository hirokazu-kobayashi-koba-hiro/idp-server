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

package org.idp.server.platform.security.ssrf;

import java.math.BigInteger;
import java.net.InetAddress;

/**
 * Defines private/reserved IP address ranges that should be blocked for SSRF protection.
 *
 * <p>Based on OWASP SSRF Prevention Cheat Sheet recommendations:
 *
 * <ul>
 *   <li>RFC1918 private networks (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)
 *   <li>Loopback addresses (127.0.0.0/8, ::1/128)
 *   <li>Link-local addresses (169.254.0.0/16, fe80::/10)
 *   <li>Cloud metadata services (169.254.169.254)
 *   <li>IPv6 private ranges (fc00::/7, fd00::/8)
 * </ul>
 *
 * @see <a
 *     href="https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html">OWASP
 *     SSRF Prevention</a>
 */
public enum PrivateIpRange {

  // IPv4 Loopback
  LOOPBACK_IPV4("127.0.0.0", 8, "IPv4 Loopback"),

  // RFC1918 Private Networks
  PRIVATE_10("10.0.0.0", 8, "RFC1918 Private Network (Class A)"),
  PRIVATE_172("172.16.0.0", 12, "RFC1918 Private Network (Class B)"),
  PRIVATE_192("192.168.0.0", 16, "RFC1918 Private Network (Class C)"),

  // Cloud Metadata Service (AWS/GCP/Azure) - specific IP
  // Must be checked before LINK_LOCAL_IPV4 as 169.254.169.254 is within 169.254.0.0/16
  CLOUD_METADATA("169.254.169.254", 32, "Cloud Metadata Service"),

  // Link-local
  LINK_LOCAL_IPV4("169.254.0.0", 16, "IPv4 Link-Local"),

  // Carrier-grade NAT (RFC6598)
  CGNAT("100.64.0.0", 10, "Carrier-Grade NAT (RFC6598)"),

  // Documentation ranges (RFC5737)
  DOCUMENTATION_1("192.0.2.0", 24, "Documentation (TEST-NET-1)"),
  DOCUMENTATION_2("198.51.100.0", 24, "Documentation (TEST-NET-2)"),
  DOCUMENTATION_3("203.0.113.0", 24, "Documentation (TEST-NET-3)"),

  // Broadcast
  BROADCAST("255.255.255.255", 32, "Broadcast"),

  // Current network (RFC1122)
  CURRENT_NETWORK("0.0.0.0", 8, "Current Network"),

  // IPv6 Loopback
  LOOPBACK_IPV6("::1", 128, "IPv6 Loopback"),

  // IPv6 Link-local
  LINK_LOCAL_IPV6("fe80::", 10, "IPv6 Link-Local"),

  // IPv6 Unique Local Address (RFC4193)
  UNIQUE_LOCAL_IPV6("fc00::", 7, "IPv6 Unique Local Address"),

  // IPv6 Site-local (deprecated but still blocked)
  SITE_LOCAL_IPV6("fec0::", 10, "IPv6 Site-Local (deprecated)"),

  // IPv4-mapped IPv6 addresses
  IPV4_MAPPED_IPV6("::ffff:0:0", 96, "IPv4-mapped IPv6");

  private final String baseAddress;
  private final int prefixLength;
  private final String description;
  private final BigInteger networkAddress;
  private final BigInteger broadcastAddress;
  private final boolean isIpv6;

  PrivateIpRange(String baseAddress, int prefixLength, String description) {
    this.baseAddress = baseAddress;
    this.prefixLength = prefixLength;
    this.description = description;
    this.isIpv6 = baseAddress.contains(":");

    try {
      InetAddress addr = InetAddress.getByName(baseAddress);
      byte[] addressBytes = addr.getAddress();
      this.networkAddress = new BigInteger(1, addressBytes);

      int totalBits = isIpv6 ? 128 : 32;
      int hostBits = totalBits - prefixLength;
      BigInteger hostMask = BigInteger.ONE.shiftLeft(hostBits).subtract(BigInteger.ONE);
      this.broadcastAddress = networkAddress.or(hostMask);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid IP range: " + baseAddress, e);
    }
  }

  public String baseAddress() {
    return baseAddress;
  }

  public int prefixLength() {
    return prefixLength;
  }

  public String description() {
    return description;
  }

  public boolean isIpv6() {
    return isIpv6;
  }

  /**
   * Checks if the given IP address falls within this range.
   *
   * @param address the IP address to check
   * @return true if the address is within this range
   */
  public boolean contains(InetAddress address) {
    byte[] addressBytes = address.getAddress();

    // IPv4 vs IPv6 mismatch check
    boolean addressIsIpv6 = addressBytes.length == 16;
    if (addressIsIpv6 != isIpv6) {
      // Special case: check IPv4-mapped IPv6 addresses
      if (addressIsIpv6 && !isIpv6) {
        // Extract IPv4 portion from IPv4-mapped IPv6 (::ffff:x.x.x.x)
        if (isIpv4MappedIpv6(addressBytes)) {
          byte[] ipv4Bytes = new byte[4];
          System.arraycopy(addressBytes, 12, ipv4Bytes, 0, 4);
          BigInteger ipv4Value = new BigInteger(1, ipv4Bytes);
          return ipv4Value.compareTo(networkAddress) >= 0
              && ipv4Value.compareTo(broadcastAddress) <= 0;
        }
      }
      return false;
    }

    BigInteger addressValue = new BigInteger(1, addressBytes);
    return addressValue.compareTo(networkAddress) >= 0
        && addressValue.compareTo(broadcastAddress) <= 0;
  }

  private boolean isIpv4MappedIpv6(byte[] addressBytes) {
    if (addressBytes.length != 16) {
      return false;
    }
    // Check for ::ffff: prefix (bytes 0-9 are 0, bytes 10-11 are 0xff)
    for (int i = 0; i < 10; i++) {
      if (addressBytes[i] != 0) {
        return false;
      }
    }
    return addressBytes[10] == (byte) 0xff && addressBytes[11] == (byte) 0xff;
  }

  @Override
  public String toString() {
    return String.format("%s/%d (%s)", baseAddress, prefixLength, description);
  }
}
