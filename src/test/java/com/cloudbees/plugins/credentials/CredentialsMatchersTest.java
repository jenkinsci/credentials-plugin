package com.cloudbees.plugins.credentials;

import com.cloudbees.plugins.credentials.common.UsernameCredentials;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.cloudbees.plugins.credentials.matchers.CQLSyntaxException;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class CredentialsMatchersTest {

    @Test
    public void describe() throws Exception {
        assertThat(CredentialsMatchers.describe(CredentialsMatchers.always()), is("true"));
        assertThat(CredentialsMatchers.describe(CredentialsMatchers.never()), is("false"));
        assertThat(CredentialsMatchers.describe(CredentialsMatchers.withId("target=\"foo\"")), is("(id == \"target=\\\"foo\\\"\")"));
        assertThat(CredentialsMatchers.describe(CredentialsMatchers.allOf(
                CredentialsMatchers.anyOf(
                        CredentialsMatchers.instanceOf(UsernameCredentials.class),
                        CredentialsMatchers.withScopes(CredentialsScope.GLOBAL, CredentialsScope.USER)
                ),
                CredentialsMatchers.not(CredentialsMatchers.withUsername("bob")))), is(
                "(((instanceof com.cloudbees.plugins.credentials.common.UsernameCredentials) "
                        + "|| ((scope == com.cloudbees.plugins.credentials.CredentialsScope.GLOBAL) "
                        + "|| (scope == com.cloudbees.plugins.credentials.CredentialsScope.USER))"
                        + ") && !(username == \"bob\"))"));
    }

    @Test
    public void parse() throws Exception {
        CredentialsMatcher matcher = CredentialsMatchers.allOf(
                CredentialsMatchers.anyOf(
                        CredentialsMatchers.instanceOf(UsernameCredentials.class),
                        CredentialsMatchers.withScopes(CredentialsScope.GLOBAL, CredentialsScope.USER)
                ),
                CredentialsMatchers.not(CredentialsMatchers.withUsername("bob")));
        assertThat(CredentialsMatchers.describe(CredentialsMatchers.parse(CredentialsMatchers.describe(matcher))), is(CredentialsMatchers.describe(matcher)));
        matcher = CredentialsMatchers.allOf(
                CredentialsMatchers.anyOf(
                        CredentialsMatchers.withProperty("foo", 57.0),
                        CredentialsMatchers.withScopes(CredentialsScope.SYSTEM)
                ),
                CredentialsMatchers.not(CredentialsMatchers.withUsername("bob")));
        assertThat(CredentialsMatchers.describe(CredentialsMatchers.parse(CredentialsMatchers.describe(matcher))), is(CredentialsMatchers.describe(matcher)));
    }

    @Test
    public void parseTrue() throws Exception {
        assertThat(CredentialsMatchers.parse(CredentialsMatchers.describe(CredentialsMatchers.always())), is(CredentialsMatchers.always()));
    }

    @Test
    public void parsePropertyTest() throws Exception {
        CredentialsMatcher parsedMatcher =
                CredentialsMatchers.parse(CredentialsMatchers.describe(CredentialsMatchers.withUsername("b\tob")));
        assertThat(parsedMatcher.matches(new MyBaseStandardCredentials("b\tob")), is(true));
        assertThat(parsedMatcher.matches(new MyBaseStandardCredentials("bob")), is(false));
    }

    @Test(expected = CQLSyntaxException.class)
    public void parseInvalid() throws Exception {
        CredentialsMatchers.parse("\"bob\" == username");
    }

    @Test(expected = CQLSyntaxException.class)
    public void parseInvalid2() throws Exception {
        CredentialsMatchers.parse("id == \"id-1\" || \"id-2\"");
    }

    @Test(expected = CQLSyntaxException.class)
    public void parseInvalidMultiline() throws Exception {
        CredentialsMatchers.parse("id == \"id-1\"\n|| \"id-2\"\n&& instanceof Boolean");
    }

    @Test(expected = CQLSyntaxException.class)
    public void parseInvalid3() throws Exception {
        CredentialsMatchers.parse("id == 4foo");
    }

    @Test
    public void parseEmpty() throws Exception {
        assertThat(CredentialsMatchers.parse(""), is(CredentialsMatchers.always()));
    }

    @Test
    public void parseNegativePropertyTest() throws Exception {
        CredentialsMatcher parsedMatcher =
                CredentialsMatchers.parse(CredentialsMatchers.describe(CredentialsMatchers.not(CredentialsMatchers.withUsername("b\tob"))));
        assertThat(parsedMatcher.matches(new MyBaseStandardCredentials("b\tob")), is(false));
        assertThat(parsedMatcher.matches(new MyBaseStandardCredentials("bob")), is(true));
    }

    @Test
    public void beanMatcher() throws Exception {
        CredentialsMatcher instance = CredentialsMatchers.withProperty("username", "bob");
        assertThat(CredentialsMatchers.describe(instance), is("(username == \"bob\")"));
        assertThat(instance.matches(new MyBaseStandardCredentials("bob")), is(true));
        assertThat(instance.matches(new MyBaseStandardCredentials("ben")), is(false));
        assertThat(instance.matches(new BaseStandardCredentials(null, null) {}), is(false));
    }

    public static class MyBaseStandardCredentials extends BaseStandardCredentials {

        private final String username;

        public MyBaseStandardCredentials(String username) {
            super(null, null);
            this.username = username;
        }

        public String getUsername() {
            return username;
        }
    }
}
