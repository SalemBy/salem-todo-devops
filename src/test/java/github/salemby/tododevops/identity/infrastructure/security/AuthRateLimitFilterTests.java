package github.salemby.tododevops.identity.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimitFilterTests {
    @Test
    void ipv4AddressesAreCountedOneByOne() {
        assertEquals("203.0.113.7", AuthRateLimitFilter.clientKey("203.0.113.7"));
        assertNotEquals(AuthRateLimitFilter.clientKey("203.0.113.7"), AuthRateLimitFilter.clientKey("203.0.113.8"));
    }

    @Test
    void ipv6AddressesShareTheirSlash64() {
        // One client controls a whole /64; counting each address separately would let it rotate past the limit.
        assertEquals(AuthRateLimitFilter.clientKey("2001:db8:1:2::1"), AuthRateLimitFilter.clientKey("2001:db8:1:2:ffff::9"));
        assertNotEquals(AuthRateLimitFilter.clientKey("2001:db8:1:2::1"), AuthRateLimitFilter.clientKey("2001:db8:1:3::1"));
    }

    @Test
    void ipv4MappedIpv6IsTheIpv4Address() {
        assertEquals("203.0.113.7", AuthRateLimitFilter.clientKey("::ffff:203.0.113.7"));
    }
}
