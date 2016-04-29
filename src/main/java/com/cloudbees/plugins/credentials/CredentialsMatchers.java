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
package com.cloudbees.plugins.credentials;

import com.cloudbees.plugins.credentials.matchers.AllOfMatcher;
import com.cloudbees.plugins.credentials.matchers.AnyOfMatcher;
import com.cloudbees.plugins.credentials.matchers.ConstantMatcher;
import com.cloudbees.plugins.credentials.matchers.IdMatcher;
import com.cloudbees.plugins.credentials.matchers.InstanceOfMatcher;
import com.cloudbees.plugins.credentials.matchers.NotMatcher;
import com.cloudbees.plugins.credentials.matchers.ScopeMatcher;
import com.cloudbees.plugins.credentials.matchers.UsernameMatcher;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Some standard matchers and filtering utility methods.
 *
 * @since 1.5
 */
public class CredentialsMatchers {

    /**
     * Utility class.
     */
    private CredentialsMatchers() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Creates a matcher that always matches.
     *
     * @return a matcher that always matches.
     */
    @NonNull
    public static CredentialsMatcher always() {
        return new ConstantMatcher(true);
    }

    /**
     * Creates a matcher that never matches.
     *
     * @return a matcher that never matches.
     */
    @NonNull
    public static CredentialsMatcher never() {
        return new ConstantMatcher(false);
    }

    /**
     * Creates a matcher that inverts the supplied matcher.
     *
     * @param matcher matcher to invert.
     * @return a matcher that is the opposite of the supplied matcher.
     */
    @NonNull
    public static CredentialsMatcher not(@NonNull CredentialsMatcher matcher) {
        return new NotMatcher(matcher);
    }

    /**
     * Creates a matcher that matches credentials of the specified type.
     *
     * @param clazz the type of credential to match.
     * @return a matcher that matches credentials of the specified type.
     */
    @NonNull
    public static CredentialsMatcher instanceOf(@NonNull Class clazz) {
        return new InstanceOfMatcher(clazz);
    }

    /**
     * Creates a matcher that matches {@link com.cloudbees.plugins.credentials.common.IdCredentials} with the
     * supplied {@link com.cloudbees.plugins.credentials.common.IdCredentials#getId()}
     *
     * @param id the {@link com.cloudbees.plugins.credentials.common.IdCredentials#getId()} to match.
     * @return a matcher that matches {@link com.cloudbees.plugins.credentials.common.IdCredentials} with the
     * supplied {@link com.cloudbees.plugins.credentials.common.IdCredentials#getId()}
     */
    @NonNull
    public static CredentialsMatcher withId(@NonNull String id) {
        return new IdMatcher(id);
    }

    /**
     * Creates a matcher that matches {@link Credentials} with the supplied {@link CredentialsScope}.
     *
     * @param scope the {@link CredentialsScope} to match.
     * @return a matcher that matches {@link Credentials} with the supplied {@link CredentialsScope}.
     */
    @NonNull
    public static CredentialsMatcher withScope(@NonNull CredentialsScope scope) {
        return new ScopeMatcher(scope);
    }

    /**
     * Creates a matcher that matches {@link Credentials} with the supplied {@link CredentialsScope}.
     *
     * @param scopes the {@link CredentialsScope}s to match.
     * @return a matcher that matches {@link Credentials} with the supplied {@link CredentialsScope}s.
     */
    @NonNull
    public static CredentialsMatcher withScopes(@NonNull CredentialsScope... scopes) {
        return new ScopeMatcher(scopes);
    }

    /**
     * Creates a matcher that matches {@link Credentials} with the supplied {@link CredentialsScope}.
     *
     * @param scopes the {@link CredentialsScope}s to match.
     * @return a matcher that matches {@link Credentials} with the supplied {@link CredentialsScope}s.
     */
    @NonNull
    public static CredentialsMatcher withScopes(@NonNull Collection<CredentialsScope> scopes) {
        return new ScopeMatcher(scopes);
    }

    /**
     * Creates a matcher that matches {@link com.cloudbees.plugins.credentials.common.UsernameCredentials} with the
     * supplied {@link com.cloudbees.plugins.credentials.common.UsernameCredentials#getUsername()}
     *
     * @param username the {@link com.cloudbees.plugins.credentials.common.UsernameCredentials#getUsername()} to match.
     * @return a matcher that matches {@link com.cloudbees.plugins.credentials.common.UsernameCredentials} with the
     * supplied {@link com.cloudbees.plugins.credentials.common.UsernameCredentials#getUsername()}
     */
    @NonNull
    public static CredentialsMatcher withUsername(@NonNull String username) {
        return new UsernameMatcher(username);
    }

    /**
     * Creates a matcher that matches when all of the supplied matchers match.
     *
     * @param matchers the matchers to match.
     * @return a matcher that matches when all of the supplied matchers match.
     */
    @NonNull
    public static CredentialsMatcher allOf(@NonNull CredentialsMatcher... matchers) {
        return new AllOfMatcher(Arrays.asList(matchers));
    }

    /**
     * Creates a matcher that matches when any of the supplied matchers match.
     *
     * @param matchers the matchers to match.
     * @return a matcher that matches when any of the supplied matchers match.
     */
    @NonNull
    public static CredentialsMatcher anyOf(@NonNull CredentialsMatcher... matchers) {
        return new AnyOfMatcher(Arrays.asList(matchers));
    }

    /**
     * Creates a matcher that matches when both of the supplied matchers match.
     *
     * @param matcher1 the first matcher to match.
     * @param matcher2 the second matcher to match.
     * @return a matcher that matches when both of the supplied matchers match.
     */
    @NonNull
    public static CredentialsMatcher both(@NonNull CredentialsMatcher matcher1, @NonNull CredentialsMatcher matcher2) {
        return new AllOfMatcher(Arrays.asList(matcher1, matcher2));
    }

    /**
     * Creates a matcher that matches when either of the supplied matchers match.
     *
     * @param matcher1 the first matcher to match.
     * @param matcher2 the second matcher to match.
     * @return a matcher that matches when either of the supplied matchers match.
     */
    @NonNull
    public static CredentialsMatcher either(@NonNull CredentialsMatcher matcher1,
                                            @NonNull CredentialsMatcher matcher2) {
        return new AnyOfMatcher(Arrays.asList(matcher1, matcher2));
    }

    /**
     * Creates a matcher that matches when none of the supplied matchers match.
     *
     * @param matchers the matchers to match.
     * @return a matcher that matches when none of the supplied matchers match.
     */
    @NonNull
    public static CredentialsMatcher noneOf(@NonNull CredentialsMatcher... matchers) {
        return not(anyOf(matchers));
    }

    /**
     * Filters credentials using the supplied matcher.
     *
     * @param credentials the credentials to filter.
     * @param matcher     the matcher to match on.
     * @param <C>         the type of credentials.
     * @return only those credentials that match the supplied matcher.
     */
    @NonNull
    public static <C extends Credentials> Collection<C> filter(@NonNull Collection<C> credentials,
                                                               @NonNull CredentialsMatcher matcher) {
        Collection<C> result = credentials instanceof Set ? new LinkedHashSet<C>() : new ArrayList<C>();
        for (C credential : credentials) {
            if (credential != null && matcher.matches(credential)) {
                result.add(credential);
            }
        }
        return result;
    }

    /**
     * Filters credentials using the supplied matcher.
     *
     * @param credentials the credentials to filter.
     * @param matcher     the matcher to match on.
     * @param <C>         the type of credentials.
     * @return only those credentials that match the supplied matcher.
     */
    @NonNull
    public static <C extends Credentials> Set<C> filter(@NonNull Set<C> credentials,
                                                        @NonNull CredentialsMatcher matcher) {
        Set<C> result = new LinkedHashSet<C>();
        for (C credential : credentials) {
            if (credential != null && matcher.matches(credential)) {
                result.add(credential);
            }
        }
        return result;
    }

    /**
     * Filters credentials using the supplied matcher.
     *
     * @param credentials the credentials to filter.
     * @param matcher     the matcher to match on.
     * @param <C>         the type of credentials.
     * @return only those credentials that match the supplied matcher.
     */
    @NonNull
    public static <C extends Credentials> List<C> filter(@NonNull List<C> credentials,
                                                         @NonNull CredentialsMatcher matcher) {
        List<C> result = new ArrayList<C>();
        for (C credential : credentials) {
            if (credential != null && matcher.matches(credential)) {
                result.add(credential);
            }
        }
        return result;
    }

    /**
     * Filters credentials using the supplied matcher.
     *
     * @param credentials the credentials to filter.
     * @param matcher     the matcher to match on.
     * @param <C>         the type of credentials.
     * @return only those credentials that match the supplied matcher.
     */
    @NonNull
    public static <C extends Credentials> Iterable<C> filter(@NonNull Iterable<C> credentials,
                                                             @NonNull CredentialsMatcher matcher) {
        List<C> result = new ArrayList<C>();
        for (C credential : credentials) {
            if (credential != null && matcher.matches(credential)) {
                result.add(credential);
            }
        }
        return result;
    }

    /**
     * Filters a map keyed by credentials using the supplied matcher.
     *
     * @param credentialMap the map keyed by credentials to filter.
     * @param matcher       the matcher to match on.
     * @param <C>           the type of credentials.
     * @param <V>           the type of the map values.
     * @return only those entries with keys that that match the supplied matcher.
     */
    @NonNull
    public static <C extends Credentials, V> Map<C, V> filterKeys(@NonNull Map<C, V> credentialMap,
                                                                  @NonNull CredentialsMatcher matcher) {
        Map<C, V> result = new LinkedHashMap<C, V>();
        for (Map.Entry<C, V> credential : credentialMap.entrySet()) {
            if (credential.getKey() != null && matcher.matches(credential.getKey())) {
                result.put(credential.getKey(), credential.getValue());
            }
        }
        return result;
    }

    /**
     * Filters a map based on credential values using the supplied matcher.
     *
     * @param credentialMap the map with credentials values to filter.
     * @param matcher       the matcher to match on.
     * @param <K>           the type of the map keys.
     * @param <C>           the type of credentials.
     * @return only those entries with keys that that match the supplied matcher.
     */
    @NonNull
    public static <C extends Credentials, K> Map<K, C> filterValues(@NonNull Map<K, C> credentialMap,
                                                                    @NonNull CredentialsMatcher matcher) {
        Map<K, C> result = new LinkedHashMap<K, C>();
        for (Map.Entry<K, C> credential : credentialMap.entrySet()) {
            if (credential.getValue() != null && matcher.matches(credential.getValue())) {
                result.put(credential.getKey(), credential.getValue());
            }
        }
        return result;
    }

    /**
     * Returns the first credential from a collection that matches the supplied matcher or if none match then the
     * specified default.
     *
     * @param credentials   the credentials to select from.
     * @param matcher       the matcher.
     * @param defaultIfNone the default value if no match found.
     * @param <C>           the type of credential.
     * @return a matching credential or the supplied default.
     */
    @CheckForNull
    public static <C extends Credentials> C firstOrDefault(@NonNull Iterable<C> credentials,
                                                           @NonNull CredentialsMatcher matcher,
                                                           @CheckForNull C defaultIfNone) {
        for (C c : credentials) {
            if (matcher.matches(c)) {
                return c;
            }
        }
        return defaultIfNone;
    }

    /**
     * Returns the first credential from a collection that matches the supplied matcher or {@code null} if none match.
     *
     * @param credentials the credentials to select from.
     * @param matcher     the matcher.
     * @param <C>         the type of credential.
     * @return a matching credential or the supplied default.
     */
    @CheckForNull
    public static <C extends Credentials> C firstOrNull(@NonNull Iterable<C> credentials,
                                                        @NonNull CredentialsMatcher matcher) {
        return firstOrDefault(credentials, matcher, null);
    }
}
