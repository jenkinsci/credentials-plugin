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
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ExtensionList;
import hudson.model.Item;
import hudson.model.ItemGroup;
import java.util.ArrayList;
import java.util.List;
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
@SuppressWarnings("rawtypes") // historical mistake with ItemGroup
@WithJenkins
final class ByIdTest {

    private LazyProvider2 lp2;
    private LazyProvider3 lp3;

    private void setUp() throws Exception {
        // Verify that our test providers are at the end of the list (they start with "ZZZ"); will be after folder, system, mock, user
        assertThat(CredentialsProvider.all().get(CredentialsProvider.all().size() - 3), instanceOf(LazyProvider1.class));
        assertThat(CredentialsProvider.all().get(CredentialsProvider.all().size() - 2), instanceOf(LazyProvider2.class));
        assertThat(CredentialsProvider.all().get(CredentialsProvider.all().size() - 1), instanceOf(LazyProvider3.class));

        lp2 = ExtensionList.lookupSingleton(LazyProvider2.class);
        lp3 = ExtensionList.lookupSingleton(LazyProvider3.class);

        // Add credentials to lazy providers
        lp2.credentials.add(new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "lazy2-cred", null, "user", "pass"));
        lp3.credentials.add(new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "lazy3-cred", null, "user", "pass"));

        // Reset counters
        lp2.getByIdCalls = 0;
        lp2.listCalls = 0;
        lp3.listCalls = 0;
    }

    @Test void lazyEvaluationWithEarlyMatch(JenkinsRule r) throws Exception {
        setUp();

        // Add a credential to the system store (early provider)
        SystemCredentialsProvider.getInstance().getCredentials().add(new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "early-cred", null, "user", "pass"));
        SystemCredentialsProvider.getInstance().save();

        // Search for early credential
        var result = CredentialsProvider.findCredentialByIdInItemGroup("early-cred", IdCredentials.class, null, null, null);

        assertThat(result, notNullValue());
        assertThat(result.getId(), is("early-cred"));

        // Verify lazy providers were not consulted
        assertEquals(0, lp2.getByIdCalls, "LazyProvider2.getCredentialById should not be called");
        assertEquals(0, lp2.listCalls, "LazyProvider2.getCredentialsInItemGroup should not be called");
        assertEquals(0, lp3.listCalls, "LazyProvider3.getCredentialsInItemGroup should not be called");
    }

    @Test void lazyEvaluationWithOptimizedProvider(JenkinsRule r) throws Exception {
        setUp();
        var result = CredentialsProvider.findCredentialByIdInItemGroup("lazy2-cred", IdCredentials.class, null, null, null);
        assertThat(result, notNullValue());
        assertThat(result.getId(), is("lazy2-cred"));
        assertEquals(1, lp2.getByIdCalls, "LazyProvider2.getCredentialById should be called once");
        assertEquals(0, lp2.listCalls, "LazyProvider2.getCredentialsInItemGroup should not be called");
        assertEquals(0, lp3.listCalls, "LazyProvider3.getCredentialsInItemGroup should not be called");
    }

    @Test void lazyEvaluationWithUnoptimizedProvider(JenkinsRule r) throws Exception {
        setUp();
        var result = CredentialsProvider.findCredentialByIdInItemGroup("lazy3-cred", IdCredentials.class, null, null, null);
        assertThat(result, notNullValue());
        assertThat(result.getId(), is("lazy3-cred"));
        assertEquals(1, lp2.getByIdCalls, "LazyProvider2.getCredentialById should be called once");
        assertEquals(0, lp2.listCalls, "LazyProvider2.getCredentialsInItemGroup should not be called (uses optimized path)");
        assertEquals(1, lp3.listCalls, "LazyProvider3.getCredentialsInItemGroup should be called once");
    }

    @Test void lazyEvaluationWithNonexistentCredential(JenkinsRule r) throws Exception {
        setUp();
        var result = CredentialsProvider.findCredentialByIdInItemGroup("nonexistent", IdCredentials.class, null, null, null);
        assertThat(result, nullValue());
        assertEquals(1, lp2.getByIdCalls, "LazyProvider2.getCredentialById should be called once");
        assertEquals(0, lp2.listCalls, "LazyProvider2.getCredentialsInItemGroup should not be called (uses optimized path)");
        assertEquals(1, lp3.listCalls, "LazyProvider3.getCredentialsInItemGroup should be called once");
    }

    @TestExtension public static final class LazyProvider1 extends CredentialsProvider {
        // @TestExtension lacks ordinal, and CredentialsProvider.getDisplayName uses Class.simpleName,
        // so to sort CredentialsProvider.all we must use a special nested class name or override this:
        @Override public String getDisplayName() {
            return "ZZZ Lazy Provider #1";
        }
        @Override public <C extends Credentials> List<C> getCredentialsInItemGroup(Class<C> type, ItemGroup itemGroup, Authentication authentication, List<DomainRequirement> domainRequirements) {
            return List.of();
        }
    }

    @TestExtension public static final class LazyProvider2 extends CredentialsProvider {
        final List<IdCredentials> credentials = new ArrayList<>();
        int getByIdCalls = 0;
        int listCalls = 0;
        @Override public String getDisplayName() {
            return "ZZZ Lazy Provider #2 (Optimized)";
        }
        @Override public <C extends IdCredentials> C getCredentialByIdInItemGroup(String id, Class<C> type, ItemGroup<?> itemGroup, Authentication authentication, List<DomainRequirement> domainRequirements) {
            getByIdCalls++;
            return find(id, type);
        }
        @Override public <C extends IdCredentials> C getCredentialByIdInItem(String id, Class<C> type, Item item, Authentication authentication, List<DomainRequirement> domainRequirements) {
            getByIdCalls++;
            return find(id, type);
        }
        private <C extends IdCredentials> @CheckForNull C find(String id, Class<C> type) {
            return type.cast(credentials.stream().filter(cred -> cred.getId().equals(id) && type.isInstance(cred)).findAny().orElse(null));
        }
        @Override public <C extends Credentials> List<C> getCredentialsInItemGroup(Class<C> type, ItemGroup itemGroup, Authentication authentication, List<DomainRequirement> domainRequirements) {
            listCalls++;
            return filter(type, credentials);
        }
    }

    @TestExtension public static final class LazyProvider3 extends CredentialsProvider {
        final List<IdCredentials> credentials = new ArrayList<>();
        int listCalls = 0;
        @Override public String getDisplayName() {
            return "ZZZ Lazy Provider #3 (Unoptimized)";
        }
        @Override public <C extends Credentials> List<C> getCredentialsInItemGroup(Class<C> type, ItemGroup itemGroup, Authentication authentication, List<DomainRequirement> domainRequirements) {
            listCalls++;
            return filter(type, credentials);
        }
    }

    private static <C extends Credentials> List<C> filter(Class<C> type, List<IdCredentials> credentials) {
        return credentials.stream().filter(type::isInstance).map(type::cast).toList();
    }

}
