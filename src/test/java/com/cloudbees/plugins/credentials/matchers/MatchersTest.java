/*
 * The MIT License
 *
 * Copyright (c) 2011-2012, CloudBees, Inc., Stephen Connolly.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.plugins.credentials.matchers;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.UsernameCredentials;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the record-based matcher implementations, covering compact
 * constructors, pattern matching branches, and alternative constructors.
 */
class MatchersTest {

    // ---- AllOfMatcher ----

    @Test
    void allOfMatcherHandlesNullList() {
        AllOfMatcher matcher = new AllOfMatcher(null);
        assertNotNull(matcher.matchers());
        assertTrue(matcher.matchers().isEmpty());
    }

    @Test
    void allOfMatcherDefensiveCopy() {
        CredentialsMatcher always = new ConstantMatcher(true);
        List<CredentialsMatcher> original = new java.util.ArrayList<>();
        original.add(always);
        AllOfMatcher matcher = new AllOfMatcher(original);
        original.clear();
        assertFalse(matcher.matchers().isEmpty(), "Should not be affected by mutation of original list");
    }

    @Test
    void allOfMatcherMatchesWhenAllMatch() {
        Credentials cred = mock(Credentials.class);
        AllOfMatcher matcher = new AllOfMatcher(List.of(
                new ConstantMatcher(true),
                new ConstantMatcher(true)));
        assertTrue(matcher.matches(cred));
    }

    @Test
    void allOfMatcherFailsWhenOneDoesNotMatch() {
        Credentials cred = mock(Credentials.class);
        AllOfMatcher matcher = new AllOfMatcher(List.of(
                new ConstantMatcher(true),
                new ConstantMatcher(false)));
        assertFalse(matcher.matches(cred));
    }

    // ---- AnyOfMatcher ----

    @Test
    void anyOfMatcherHandlesNullList() {
        AnyOfMatcher matcher = new AnyOfMatcher(null);
        assertNotNull(matcher.matchers());
        assertTrue(matcher.matchers().isEmpty());
    }

    @Test
    void anyOfMatcherDefensiveCopy() {
        CredentialsMatcher always = new ConstantMatcher(true);
        List<CredentialsMatcher> original = new java.util.ArrayList<>();
        original.add(always);
        AnyOfMatcher matcher = new AnyOfMatcher(original);
        original.clear();
        assertFalse(matcher.matchers().isEmpty(), "Should not be affected by mutation of original list");
    }

    @Test
    void anyOfMatcherMatchesWhenOneMatches() {
        Credentials cred = mock(Credentials.class);
        AnyOfMatcher matcher = new AnyOfMatcher(List.of(
                new ConstantMatcher(false),
                new ConstantMatcher(true)));
        assertTrue(matcher.matches(cred));
    }

    @Test
    void anyOfMatcherFailsWhenNoneMatch() {
        Credentials cred = mock(Credentials.class);
        AnyOfMatcher matcher = new AnyOfMatcher(List.of(
                new ConstantMatcher(false),
                new ConstantMatcher(false)));
        assertFalse(matcher.matches(cred));
    }

    // ---- IdMatcher ----

    @Test
    void idMatcherRejectsNonIdCredentials() {
        Credentials cred = mock(Credentials.class);
        IdMatcher matcher = new IdMatcher("test-id");
        assertFalse(matcher.matches(cred), "Should not match non-IdCredentials");
    }

    @Test
    void idMatcherMatchesCorrectId() {
        IdCredentials cred = mock(IdCredentials.class);
        when(cred.getId()).thenReturn("test-id");
        IdMatcher matcher = new IdMatcher("test-id");
        assertTrue(matcher.matches(cred));
    }

    @Test
    void idMatcherRejectsMismatchedId() {
        IdCredentials cred = mock(IdCredentials.class);
        when(cred.getId()).thenReturn("other-id");
        IdMatcher matcher = new IdMatcher("test-id");
        assertFalse(matcher.matches(cred));
    }

    @Test
    void idMatcherRejectsNullId() {
        assertThrows(NullPointerException.class, () -> new IdMatcher(null));
    }

    // ---- UsernameMatcher ----

    @Test
    void usernameMatcherCompactConstructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new UsernameMatcher(null));
    }

    @Test
    void usernameMatcherRejectsNonUsernameCredentials() {
        Credentials cred = mock(Credentials.class);
        UsernameMatcher matcher = new UsernameMatcher("admin");
        assertFalse(matcher.matches(cred), "Should not match non-UsernameCredentials");
    }

    @Test
    void usernameMatcherMatchesCorrectUsername() {
        UsernameCredentials cred = mock(UsernameCredentials.class);
        when(cred.getUsername()).thenReturn("admin");
        UsernameMatcher matcher = new UsernameMatcher("admin");
        assertTrue(matcher.matches(cred));
    }

    @Test
    void usernameMatcherRejectsMismatchedUsername() {
        UsernameCredentials cred = mock(UsernameCredentials.class);
        when(cred.getUsername()).thenReturn("user");
        UsernameMatcher matcher = new UsernameMatcher("admin");
        assertFalse(matcher.matches(cred));
    }

    // ---- InstanceOfMatcher ----

    @Test
    void instanceOfMatcherCompactConstructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new InstanceOfMatcher(null));
    }

    @Test
    void instanceOfMatcherMatchesCorrectType() {
        IdCredentials cred = mock(IdCredentials.class);
        InstanceOfMatcher matcher = new InstanceOfMatcher(IdCredentials.class);
        assertTrue(matcher.matches(cred));
    }

    @Test
    void instanceOfMatcherRejectsWrongType() {
        Credentials cred = mock(Credentials.class);
        InstanceOfMatcher matcher = new InstanceOfMatcher(IdCredentials.class);
        assertFalse(matcher.matches(cred));
    }

    // ---- ScopeMatcher ----

    @Test
    void scopeMatcherVarargsConstructor() {
        ScopeMatcher matcher = new ScopeMatcher(CredentialsScope.GLOBAL, CredentialsScope.SYSTEM);
        assertTrue(matcher.scopes().contains(CredentialsScope.GLOBAL));
        assertTrue(matcher.scopes().contains(CredentialsScope.SYSTEM));
    }

    @Test
    void scopeMatcherCollectionConstructor() {
        ScopeMatcher matcher = new ScopeMatcher(
                Arrays.asList(CredentialsScope.GLOBAL, CredentialsScope.USER));
        assertTrue(matcher.scopes().contains(CredentialsScope.GLOBAL));
        assertTrue(matcher.scopes().contains(CredentialsScope.USER));
    }

    @Test
    void scopeMatcherSingleScopeConstructor() {
        ScopeMatcher matcher = new ScopeMatcher(CredentialsScope.SYSTEM);
        assertEquals(Collections.singleton(CredentialsScope.SYSTEM), matcher.scopes());
    }

    @Test
    void scopeMatcherMatchesCredentialWithMatchingScope() {
        Credentials cred = mock(Credentials.class);
        when(cred.getScope()).thenReturn(CredentialsScope.GLOBAL);
        ScopeMatcher matcher = new ScopeMatcher(CredentialsScope.GLOBAL, CredentialsScope.SYSTEM);
        assertTrue(matcher.matches(cred));
    }

    @Test
    void scopeMatcherRejectsCredentialWithNonMatchingScope() {
        Credentials cred = mock(Credentials.class);
        when(cred.getScope()).thenReturn(CredentialsScope.USER);
        ScopeMatcher matcher = new ScopeMatcher(CredentialsScope.GLOBAL, CredentialsScope.SYSTEM);
        assertFalse(matcher.matches(cred));
    }

    @Test
    void scopeMatcherCanonicalConstructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new ScopeMatcher((java.util.Set<CredentialsScope>) null));
    }

    // ---- NotMatcher ----

    @Test
    void notMatcherCompactConstructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new NotMatcher(null));
    }

    @Test
    void notMatcherInvertsResult() {
        Credentials cred = mock(Credentials.class);
        NotMatcher matcher = new NotMatcher(new ConstantMatcher(true));
        assertFalse(matcher.matches(cred));

        NotMatcher matcher2 = new NotMatcher(new ConstantMatcher(false));
        assertTrue(matcher2.matches(cred));
    }

    // ---- ConstantMatcher ----

    @Test
    void constantMatcherAlwaysReturnsConfiguredValue() {
        Credentials cred = mock(Credentials.class);
        assertTrue(new ConstantMatcher(true).matches(cred));
        assertFalse(new ConstantMatcher(false).matches(cred));
    }

    // ---- Record equality/toString (auto-generated by records) ----

    @Test
    void recordEqualityAndHashCode() {
        IdMatcher a = new IdMatcher("id1");
        IdMatcher b = new IdMatcher("id1");
        IdMatcher c = new IdMatcher("id2");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void recordToStringIsReadable() {
        IdMatcher matcher = new IdMatcher("my-id");
        String str = matcher.toString();
        assertTrue(str.contains("my-id"), "toString should contain the id value");
    }
}
