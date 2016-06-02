package com.cloudbees.plugins.credentials;

import com.cloudbees.plugins.credentials.common.UsernameCredentials;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class CredentialsMatchersTest {

    @Test
    public void describe() throws Exception {
        assertThat(CredentialsMatchers.describe(CredentialsMatchers.always()), is("true"));
        assertThat(CredentialsMatchers.describe(CredentialsMatchers.never()), is("false"));
        assertThat(CredentialsMatchers.describe(CredentialsMatchers.withId("target=\"foo\"")), is("(c.id == \"target=\\\"foo\\\"\")"));
        assertThat(CredentialsMatchers.describe(CredentialsMatchers.allOf(
                CredentialsMatchers.anyOf(
                        CredentialsMatchers.instanceOf(UsernameCredentials.class),
                        CredentialsMatchers.withScopes(CredentialsScope.GLOBAL, CredentialsScope.USER)
                ),
                CredentialsMatchers.not(CredentialsMatchers.withUsername("bob")))), is(
                "(((c instanceof com.cloudbees.plugins.credentials.common.UsernameCredentials) "
                        + "|| (c.scope == com.cloudbees.plugins.credentials.CredentialsScope.GLOBAL "
                        + "|| c.scope == com.cloudbees.plugins.credentials.CredentialsScope.USER)"
                        + ") && !((c.username == \"bob\")))"));
    }
}
