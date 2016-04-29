/*
 * The MIT License
 *
 * Copyright (c) 2011-2013, CloudBees, Inc., Stephen Connolly.
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

package com.cloudbees.plugins.credentials.domains;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.CopyOnWriteMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Represents a {@link Domain} and an associated set of {@link Credentials}.
 *
 * @since 1.5
 */
public class DomainCredentials {
    /**
     * The domain that these credentials are scoped to.
     */
    @NonNull
    private final Domain domain;
    /**
     * The credentials scoped to this domain.
     */
    @NonNull
    private final List<Credentials> credentials;

    /**
     * Stapler's constructor.
     *
     * @param domain      the domain.
     * @param credentials the credentials.
     */
    @DataBoundConstructor
    public DomainCredentials(Domain domain, List<Credentials> credentials) {
        this.domain = domain == null ? Domain.global() : domain.resolve();
        this.credentials = credentials == null ? new ArrayList<Credentials>() : new ArrayList<Credentials>(credentials);
    }

    /**
     * Converts a {@link Collection} of {@link DomainCredentials} into a {@link Map} keyed by {@link Domain} with
     * {@link List} of {@link Credentials} as values.
     *
     * @param collection the collection.
     * @return the corresponding map.
     */
    @NonNull
    public static Map<Domain, List<Credentials>> asMap(@CheckForNull Collection<DomainCredentials> collection) {
        Map<Domain, List<Credentials>> map = new LinkedHashMap<Domain, List<Credentials>>();
        if (collection != null) {
            for (DomainCredentials item : collection) {
                List<Credentials> existing = map.get(item.getDomain());
                if (existing == null) {
                    map.put(item.getDomain(), new CopyOnWriteArrayList<Credentials>(item.getCredentials()));
                } else {
                    // allow combining for malformed requests
                    existing.addAll(item.getCredentials());
                }
            }
        }
        return new CopyOnWriteMap.Hash<Domain, List<Credentials>>(map);
    }

    /**
     * Converts a {@link Map} keyed by {@link Domain} with {@link List} of {@link Credentials} as values into a
     * {@link List} of {@link DomainCredentials} into a
     *
     * @param map the map.
     * @return the corresponding list.
     */
    @NonNull
    public static List<DomainCredentials> asList(Map<Domain, List<Credentials>> map) {
        List<DomainCredentials> result = new ArrayList<DomainCredentials>();
        if (map != null) {
            for (Map.Entry<Domain, List<Credentials>> entry : map.entrySet()) {
                result.add(new DomainCredentials(entry.getKey(), entry.getValue()));
            }
        }
        return result;
    }

    /**
     * Converts a {@link Map} keyed by {@link Domain} with {@link List} of {@link Credentials} as values into a
     * {@link List} of {@link DomainCredentials} into a
     *
     * @param map the map.
     * @return the corresponding list.
     */
    @NonNull
    public static Map<Domain, List<Credentials>> toCopyOnWriteMap(@CheckForNull Map<Domain, List<Credentials>> map) {
        if (map instanceof CopyOnWriteMap.Hash) {
            // if we get this far, likely we will find all entries as CopyOnWriteArrayList
            // so we should almost always be returning after one iteration.
            boolean allCopyOnWrite = true;
            for (List<Credentials> list : map.values()) {
                if (!(list instanceof CopyOnWriteArrayList)) {
                    allCopyOnWrite = false;
                    break;
                }
            }
            if (allCopyOnWrite) {
                return map;
            }
        }
        Map<Domain, List<Credentials>> tmp = new LinkedHashMap<Domain, List<Credentials>>();
        if (map != null) {
            for (Map.Entry<Domain, List<Credentials>> entry : map.entrySet()) {
                tmp.put(entry.getKey() == null
                                ? Domain.global()
                                : entry.getKey().resolve(),
                        new CopyOnWriteArrayList<Credentials>(
                                entry.getValue() == null
                                        ? Collections.<Credentials>emptyList()
                                        : entry.getValue()));
            }
        }
        return new CopyOnWriteMap.Hash<Domain, List<Credentials>>(tmp);
    }

    /**
     * Handle migration of standard storage method for pre-domain data into domain segmented data.
     *
     * @param map  the new map based store.
     * @param list the old list based store.
     * @return consolidated map based store.
     */
    public static Map<Domain, List<Credentials>> migrateListToMap(@CheckForNull Map<Domain, List<Credentials>> map,
                                                                  @CheckForNull List<Credentials> list) {
        if (map == null) {
            map = new CopyOnWriteMap.Hash<Domain, List<Credentials>>();
        }
        if (!map.containsKey(Domain.global())) {
            if (list == null) {
                map.put(Domain.global(), new CopyOnWriteArrayList<Credentials>());
            } else {
                map.put(Domain.global(), new CopyOnWriteArrayList<Credentials>(list));
            }
        }
        return map;
    }

    /**
     * Helper to assist retrieving credentials from the map based store.
     *
     * @param domainCredentialsMap map of credentials by domain.
     * @param type                 type of credential to retrieve.
     * @param domainRequirements   domain requirements.
     * @param credentialsMatcher   what subset of credentials to match.
     * @param <C>                  the type of credential to retrieve.
     * @return a {@link List} of matching credentials.
     */
    @NonNull
    public static <C extends Credentials> List<C> getCredentials(
            @NonNull Map<Domain, List<Credentials>> domainCredentialsMap,
            @NonNull Class<C> type,
            @NonNull List<DomainRequirement> domainRequirements,
            @NonNull CredentialsMatcher credentialsMatcher) {
        List<C> result = new ArrayList<C>();
        for (Map.Entry<Domain, List<Credentials>> entry : domainCredentialsMap.entrySet()) {
            if (entry.getKey().test(domainRequirements)) {
                for (Credentials credential : entry.getValue()) {
                    if (!type.isInstance(credential)) {
                        continue;
                    }
                    // If the credentials have a native restriction that isn't imposed
                    // by the Domain, give the Credentials a chance to self-restrict
                    // themselves from being surfaced.
                    if (credential instanceof DomainRestrictedCredentials
                            && !((DomainRestrictedCredentials) credential).matches(domainRequirements)) {
                        continue;
                    }
                    if (credentialsMatcher.matches(credential)) {
                        result.add(type.cast(credential));
                    }
                }
            }
        }
        return result;
    }

    /**
     * Helper method used by the {@code domainCredentials.jelly} taglib to ensure the list is valid.
     *
     * @param list the list.
     * @return the list with fixes applied.
     */
    @NonNull
    public static List<DomainCredentials> fixList(@CheckForNull List<DomainCredentials> list) {
        Map<Domain, List<Credentials>> map = asMap(list);
        if (!map.containsKey(Domain.global())) {
            map.put(Domain.global(), new CopyOnWriteArrayList<Credentials>());
        }
        return asList(map);
    }

    /**
     * Returns the domain.
     *
     * @return the domain.
     */
    @NonNull
    @SuppressWarnings("unused") // by stapler
    public Domain getDomain() {
        return domain;
    }

    /**
     * Returns the credentials.
     *
     * @return the credentials.
     */
    @NonNull
    @SuppressWarnings("unused") // by stapler
    public List<Credentials> getCredentials() {
        return credentials;
    }
}
