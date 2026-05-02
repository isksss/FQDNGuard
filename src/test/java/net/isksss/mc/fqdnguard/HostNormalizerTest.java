package net.isksss.mc.fqdnguard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HostNormalizerTest {

  @Test
  void normalizeLowercasesAndRemovesTrailingDots() {
    assertEquals("mc.example.com", HostNormalizer.normalize(" MC.Example.COM.. "));
  }

  @Test
  void normalizeReturnsEmptyStringForBlankHost() {
    assertEquals("", HostNormalizer.normalize("   "));
  }

  @Test
  void normalizeConvertsInternationalDomainNameToAscii() {
    assertEquals("xn--r8jz45g.example", HostNormalizer.normalize("例え.example"));
  }
}
