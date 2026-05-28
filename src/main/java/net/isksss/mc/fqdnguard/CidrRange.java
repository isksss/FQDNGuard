package net.isksss.mc.fqdnguard;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Optional;

/** IPv4 または IPv6 の CIDR 範囲を表す。 */
record CidrRange(String value, InetAddress networkAddress, int prefixLength) {

  /**
   * CIDR 表記を解析する。
   *
   * @param value 解析する CIDR 表記
   * @return 解析できた CIDR 範囲
   */
  static Optional<CidrRange> parse(String value) {
    String normalized = value.trim().toLowerCase();
    int separatorIndex = normalized.indexOf('/');
    if (separatorIndex <= 0 || separatorIndex == normalized.length() - 1) {
      return Optional.empty();
    }

    String addressValue = normalized.substring(0, separatorIndex);
    String prefixValue = normalized.substring(separatorIndex + 1);
    if (!IpNormalizer.isIpLiteral(addressValue)) {
      return Optional.empty();
    }

    try {
      InetAddress address = InetAddress.getByName(addressValue);
      int maxPrefixLength = address.getAddress().length * Byte.SIZE;
      int prefixLength = Integer.parseInt(prefixValue);
      if (prefixLength < 0 || prefixLength > maxPrefixLength) {
        return Optional.empty();
      }

      InetAddress networkAddress =
          InetAddress.getByAddress(mask(address.getAddress(), prefixLength));
      return Optional.of(
          new CidrRange(addressValue + "/" + prefixLength, networkAddress, prefixLength));
    } catch (NumberFormatException | UnknownHostException exception) {
      return Optional.empty();
    }
  }

  /**
   * IP アドレスが CIDR 範囲内か判定する。
   *
   * @param ip 判定する IP アドレス
   * @return 範囲内の場合は true
   */
  boolean contains(String ip) {
    if (!IpNormalizer.isIpLiteral(ip)) {
      return false;
    }

    try {
      InetAddress address = InetAddress.getByName(ip);
      byte[] networkBytes = networkAddress.getAddress();
      byte[] addressBytes = address.getAddress();
      return networkBytes.length == addressBytes.length
          && Arrays.equals(networkBytes, mask(addressBytes, prefixLength));
    } catch (UnknownHostException exception) {
      return false;
    }
  }

  private static byte[] mask(byte[] address, int prefixLength) {
    BigInteger value = new BigInteger(1, address);
    int bitLength = address.length * Byte.SIZE;
    BigInteger mask =
        BigInteger.ONE
            .shiftLeft(bitLength)
            .subtract(BigInteger.ONE)
            .shiftRight(bitLength - prefixLength)
            .shiftLeft(bitLength - prefixLength);
    byte[] masked = value.and(mask).toByteArray();
    byte[] result = new byte[address.length];
    int copyLength = Math.min(masked.length, result.length);
    System.arraycopy(
        masked, masked.length - copyLength, result, result.length - copyLength, copyLength);
    return result;
  }
}
