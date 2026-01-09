package com.cloudbees.plugins.credentials.impl;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisabledOnOs(OS.WINDOWS)
class UsernamePasswordCredentialsImplFIPSTest {

    @RegisterExtension
    private final RealJenkinsExtension extension = new RealJenkinsExtension().javaOptions("-Djenkins.security.FIPS140.COMPLIANCE=true", "-Xmx512M")
            .withDebugPort(8000).withDebugServer(true).withDebugSuspend(true);

    @Test
    void nonCompliantLaunchExceptionTest() throws Throwable {
        extension.then(r -> {
            new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "all-good-beer", "Best captain and player in the world",
                    "Pat Cummins", "theaustraliancricketteamisthebest");
            assertThrows(Descriptor.FormException.class, () -> new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "bad-foo", "someone",
                    "Virat", "tooshort"));
            assertThrows(Descriptor.FormException.class, () -> new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "bad-bar", "duck",
                    "Rohit", ""));
            assertThrows(Descriptor.FormException.class, () -> new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "bad-foo", "not too bad",
                    "Gill", null));
        });
    }

    @Test
    void invalidIsNotSavedInFIPSModeTest() throws Throwable {
        extension.then(r -> {
            UsernamePasswordCredentialsImpl entry = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "all-good", "Best captain and player in the world",
                    "Pat Cummins", "theaustraliancricketteamisthebest");
            CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.get()).iterator().next();
            store.addCredentials(Domain.global(), entry);
            store.save();
            // Valid password is saved
            UsernamePasswordCredentialsImpl cred = CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentialsInItem(UsernamePasswordCredentialsImpl.class, null, ACL.SYSTEM2),
                    CredentialsMatchers.withId("all-good"));
            assertThat(cred, notNullValue());
            assertThrows(Descriptor.FormException.class, () -> store.addCredentials(Domain.global(),  new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "all-good", "someone",
                    "foo", "tooshort")));
            store.save();
            // Invalid password size threw an exception, so it wasn't saved
            cred = CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentialsInItem(UsernamePasswordCredentialsImpl.class, null, ACL.SYSTEM2),
                    CredentialsMatchers.withId("all-good"));
            assertThat(cred, notNullValue());
            assertThat(cred.getPassword().getPlainText(), is("theaustraliancricketteamisthebest"));
        });
    }

    @Test
    void formValidationTest() throws Throwable {
        extension.then(r -> {
            UsernamePasswordCredentialsImpl.DescriptorImpl descriptor = ExtensionList.lookupSingleton(UsernamePasswordCredentialsImpl.DescriptorImpl.class);
            FormValidation result = descriptor.doCheckPassword("theaustraliancricketteamisthebest");
            assertThat(result.getMessage(), nullValue());
            result = descriptor.doCheckPassword("foo");
            assertThat(result.getMessage(), not(emptyString()));
            assertThat(result.getMessage(), containsString(StringEscapeUtils.escapeHtml4(Messages.passwordTooShortFIPS())));
        });
    }

}
