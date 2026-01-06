/*
 * The MIT License
 *
 * Copyright 2026 CloudBees, Inc.
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

import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import jenkins.model.Jenkins;
import org.springframework.security.core.Authentication;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Exercises {@link CredentialsProvider} searches for {@link IdCredentials}.
 */
@WithJenkins
final class ByIdTest {

    @Test void providerOrder(JenkinsRule r) {
        CredentialsProvider.all().forEach(System.out::println);
        // Verify that our test providers are at the end of the list (they start with "ZZZ")
        assertThat(CredentialsProvider.all().get(CredentialsProvider.all().size() - 1), instanceOf(LazyProvider3.class));
        assertThat(CredentialsProvider.all().get(CredentialsProvider.all().size() - 2), instanceOf(LazyProvider2.class));
        assertThat(CredentialsProvider.all().get(CredentialsProvider.all().size() - 3), instanceOf(LazyProvider1.class));
    }

    @Test void lazyEvaluationWithEarlyMatch(JenkinsRule r) throws Exception {
        // Add a credential to the system store (early provider)
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new DummyIdCredentials(CredentialsScope.GLOBAL, "early-cred", "Early Credential")
        );
        SystemCredentialsProvider.getInstance().save();

        // Add credentials to lazy providers
        LazyProvider2.addCredential(new DummyIdCredentials(CredentialsScope.GLOBAL, "lazy2-cred", "Lazy2 Credential"));
        LazyProvider3.addCredential(new DummyIdCredentials(CredentialsScope.GLOBAL, "lazy3-cred", "Lazy3 Credential"));

        // Reset counters
        LazyProvider2.resetCounters();
        LazyProvider3.resetCounters();

        // Search for early credential
        IdCredentials result = CredentialsProvider.findCredentialById(
                "early-cred",
                IdCredentials.class,
                (ItemGroup) null,
                ACL.SYSTEM2,
                Collections.emptyList()
        );

        assertThat(result, notNullValue());
        assertThat(result.getId(), is("early-cred"));

        // Verify lazy providers were not consulted
        assertEquals(0, LazyProvider2.getByIdCalls.get(), "LazyProvider2.getCredentialById should not be called");
        assertEquals(0, LazyProvider2.listCalls.get(), "LazyProvider2.getCredentialsInItemGroup should not be called");
        assertEquals(0, LazyProvider3.listCalls.get(), "LazyProvider3.getCredentialsInItemGroup should not be called");
    }

    @Test void lazyEvaluationWithOptimizedProvider(JenkinsRule r) {
        // Add credentials to lazy providers
        LazyProvider2.addCredential(new DummyIdCredentials(CredentialsScope.GLOBAL, "lazy2-cred", "Lazy2 Credential"));
        LazyProvider3.addCredential(new DummyIdCredentials(CredentialsScope.GLOBAL, "lazy3-cred", "Lazy3 Credential"));

        // Reset counters
        LazyProvider2.resetCounters();
        LazyProvider3.resetCounters();

        // Search for lazy2 credential
        IdCredentials result = CredentialsProvider.findCredentialById(
                "lazy2-cred",
                IdCredentials.class,
                (ItemGroup) null,
                ACL.SYSTEM2,
                Collections.emptyList()
        );

        assertThat(result, notNullValue());
        assertThat(result.getId(), is("lazy2-cred"));

        // Verify LazyProvider2's optimized method was called
        assertEquals(1, LazyProvider2.getByIdCalls.get(), "LazyProvider2.getCredentialById should be called once");
        // Verify LazyProvider2's list method was NOT called (optimized path)
        assertEquals(0, LazyProvider2.listCalls.get(), "LazyProvider2.getCredentialsInItemGroup should not be called");
        // Verify LazyProvider3 was not consulted at all
        assertEquals(0, LazyProvider3.listCalls.get(), "LazyProvider3.getCredentialsInItemGroup should not be called");
    }

    @Test void lazyEvaluationWithUnoptimizedProvider(JenkinsRule r) {
        // Add credentials to lazy providers
        LazyProvider2.addCredential(new DummyIdCredentials(CredentialsScope.GLOBAL, "lazy2-cred", "Lazy2 Credential"));
        LazyProvider3.addCredential(new DummyIdCredentials(CredentialsScope.GLOBAL, "lazy3-cred", "Lazy3 Credential"));

        // Reset counters
        LazyProvider2.resetCounters();
        LazyProvider3.resetCounters();

        // Search for lazy3 credential
        IdCredentials result = CredentialsProvider.findCredentialById(
                "lazy3-cred",
                IdCredentials.class,
                (ItemGroup) null,
                ACL.SYSTEM2,
                Collections.emptyList()
        );

        assertThat(result, notNullValue());
        assertThat(result.getId(), is("lazy3-cred"));

        // Verify LazyProvider2 was consulted but didn't find it
        assertEquals(1, LazyProvider2.getByIdCalls.get(), "LazyProvider2.getCredentialById should be called once");
        assertEquals(0, LazyProvider2.listCalls.get(), "LazyProvider2.getCredentialsInItemGroup should not be called (uses optimized path)");
        // Verify LazyProvider3's list method WAS called (unoptimized, uses default implementation)
        assertEquals(1, LazyProvider3.listCalls.get(), "LazyProvider3.getCredentialsInItemGroup should be called once");
    }

    @Test void lazyEvaluationWithNonexistentCredential(JenkinsRule r) {
        // Add credentials to lazy providers
        LazyProvider2.addCredential(new DummyIdCredentials(CredentialsScope.GLOBAL, "lazy2-cred", "Lazy2 Credential"));
        LazyProvider3.addCredential(new DummyIdCredentials(CredentialsScope.GLOBAL, "lazy3-cred", "Lazy3 Credential"));

        // Reset counters
        LazyProvider2.resetCounters();
        LazyProvider3.resetCounters();

        // Search for nonexistent credential
        IdCredentials result = CredentialsProvider.findCredentialById(
                "nonexistent",
                IdCredentials.class,
                (ItemGroup) null,
                ACL.SYSTEM2,
                Collections.emptyList()
        );

        assertThat(result, nullValue());

        // Verify both lazy providers were consulted
        assertEquals(1, LazyProvider2.getByIdCalls.get(), "LazyProvider2.getCredentialById should be called once");
        assertEquals(0, LazyProvider2.listCalls.get(), "LazyProvider2.getCredentialsInItemGroup should not be called (uses optimized path)");
        assertEquals(1, LazyProvider3.listCalls.get(), "LazyProvider3.getCredentialsInItemGroup should be called once");
    }

    @TestExtension public static final class LazyProvider1 extends CredentialsProvider {

        // @TestExtension lacks ordinal, and CredentialsProvider.getDisplayName uses Class.simpleName,
        // so to sort CredentialsProvider.all we must use a special nested class name or override this:
        @Override public String getDisplayName() {
            return "ZZZ Lazy Provider #1";
        }

        @Override
        @NonNull
        public <C extends Credentials> List<C> getCredentialsInItemGroup(@NonNull Class<C> type,
                                                                          @NonNull ItemGroup itemGroup,
                                                                          @NonNull Authentication authentication,
                                                                          @NonNull List<DomainRequirement> domainRequirements) {
            return Collections.emptyList();
        }

    }

    /**
     * A lazy provider that implements the optimized getCredentialById methods.
     */
    @TestExtension public static final class LazyProvider2 extends CredentialsProvider {

        private static final List<IdCredentials> credentials = new java.util.concurrent.CopyOnWriteArrayList<>();
        static final AtomicInteger getByIdCalls = new AtomicInteger(0);
        static final AtomicInteger listCalls = new AtomicInteger(0);

        static void addCredential(IdCredentials credential) {
            credentials.add(credential);
        }

        static void resetCounters() {
            getByIdCalls.set(0);
            listCalls.set(0);
        }

        @Override public String getDisplayName() {
            return "ZZZ Lazy Provider #2 (Optimized)";
        }

        @Override
        @CheckForNull
        public <C extends IdCredentials> C getCredentialById(@NonNull String id,
                                                              @NonNull Class<C> type,
                                                              @NonNull ItemGroup itemGroup,
                                                              @NonNull Authentication authentication,
                                                              @NonNull List<DomainRequirement> domainRequirements) {
            getByIdCalls.incrementAndGet();
            for (IdCredentials cred : credentials) {
                if (cred.getId().equals(id) && type.isInstance(cred)) {
                    return type.cast(cred);
                }
            }
            return null;
        }

        @Override
        @CheckForNull
        public <C extends IdCredentials> C getCredentialById(@NonNull String id,
                                                              @NonNull Class<C> type,
                                                              @NonNull Item item,
                                                              @NonNull Authentication authentication,
                                                              @NonNull List<DomainRequirement> domainRequirements) {
            getByIdCalls.incrementAndGet();
            for (IdCredentials cred : credentials) {
                if (cred.getId().equals(id) && type.isInstance(cred)) {
                    return type.cast(cred);
                }
            }
            return null;
        }

        @Override
        @NonNull
        public <C extends Credentials> List<C> getCredentialsInItemGroup(@NonNull Class<C> type,
                                                                          @NonNull ItemGroup itemGroup,
                                                                          @NonNull Authentication authentication,
                                                                          @NonNull List<DomainRequirement> domainRequirements) {
            listCalls.incrementAndGet();
            List<C> result = new java.util.ArrayList<>();
            for (IdCredentials cred : credentials) {
                if (type.isInstance(cred)) {
                    result.add(type.cast(cred));
                }
            }
            return result;
        }
    }

    /**
     * A lazy provider that does NOT implement optimized getCredentialById methods,
     * relying on default implementations.
     */
    @TestExtension public static final class LazyProvider3 extends CredentialsProvider {

        private static final List<IdCredentials> credentials = new java.util.concurrent.CopyOnWriteArrayList<>();
        static final AtomicInteger listCalls = new AtomicInteger(0);

        static void addCredential(IdCredentials credential) {
            credentials.add(credential);
        }

        static void resetCounters() {
            listCalls.set(0);
        }

        @Override public String getDisplayName() {
            return "ZZZ Lazy Provider #3 (Unoptimized)";
        }

        @Override
        @NonNull
        public <C extends Credentials> List<C> getCredentialsInItemGroup(@NonNull Class<C> type,
                                                                          @NonNull ItemGroup itemGroup,
                                                                          @NonNull Authentication authentication,
                                                                          @NonNull List<DomainRequirement> domainRequirements) {
            listCalls.incrementAndGet();
            List<C> result = new java.util.ArrayList<>();
            for (IdCredentials cred : credentials) {
                if (type.isInstance(cred)) {
                    result.add(type.cast(cred));
                }
            }
            return result;
        }
    }

    /**
     * Simple test credential with ID.
     */
    private static class DummyIdCredentials extends com.cloudbees.plugins.credentials.impl.BaseStandardCredentials {

        DummyIdCredentials(CredentialsScope scope, String id, String description) {
            super(scope, id, description);
        }
    }

}
