package com.cloudbees.plugins.credentials;

import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import org.htmlunit.WebResponse;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Items;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.xmlunit.matchers.CompareMatcher;

import static com.cloudbees.plugins.credentials.XmlMatchers.isSimilarToIgnoringPrivateAttrs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assume.assumeThat;

public class CredentialsStoreActionTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();
    private SystemCredentialsProvider.ProviderImpl system;
    private CredentialsStore systemStore;

    @Before
    public void setUp() {
        system = ExtensionList.lookup(CredentialsProvider.class).get(SystemCredentialsProvider.ProviderImpl.class);
        systemStore = system.getStore(j.getInstance());
    }

    @Test
    public void smokes() throws Exception {
        List<Domain> domainList = new ArrayList<>(systemStore.getDomains());
        domainList.remove(Domain.global());
        for (Domain d : domainList) {
            systemStore.removeDomain(d);
        }

        List<Credentials> credentialsList = new ArrayList<>(systemStore.getCredentials(Domain.global()));
        for (Credentials c : credentialsList) {
            systemStore.removeCredentials(Domain.global(), c);
        }

        JenkinsRule.WebClient wc = j.createWebClient();
        WebResponse response = wc.goTo("credentials/store/system/api/xml?depth=5", "application/xml").getWebResponse();
        assertThat(response.getContentAsString(), isSimilarToIgnoringPrivateAttrs("<userFacingAction>"
                + "<domains>"
                + "<_>"
                + "<description>"
                + "Credentials that should be available irrespective of domain specification to requirements "
                + "matching."
                + "</description>"
                + "<displayName>Global credentials (unrestricted)</displayName>"
                + "<fullDisplayName>System » Global credentials (unrestricted)</fullDisplayName>"
                + "<fullName>system/_</fullName>"
                + "<global>true</global>"
                + "<urlName>_</urlName>"
                + "</_>"
                + "</domains>"
                + "</userFacingAction>").ignoreWhitespace().ignoreComments());

        Random entropy = new Random();
        String domainName = "test" + entropy.nextInt();
        String domainDescription = "test description " + entropy.nextLong();
        String credentialId = "test-id-" + entropy.nextInt();
        String credentialDescription = "test-account-" + entropy.nextInt();
        String credentialUsername = "test-user-" + entropy.nextInt();
        systemStore.addDomain(new Domain(domainName, domainDescription, Collections.emptyList()),
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, credentialId,
                        credentialDescription, credentialUsername, "test-secret"));
        response = wc.goTo("credentials/store/system/api/xml?depth=5", "application/xml").getWebResponse();
        assertThat(response.getContentAsString(), isSimilarToIgnoringPrivateAttrs("<userFacingAction>"
                + "<domains>"
                + "<_>"
                + "<description>"
                + "Credentials that should be available irrespective of domain specification to requirements "
                + "matching."
                + "</description>"
                + "<displayName>Global credentials (unrestricted)</displayName>"
                + "<fullDisplayName>System » Global credentials (unrestricted)</fullDisplayName>"
                + "<fullName>system/_</fullName>"
                + "<global>true</global>"
                + "<urlName>_</urlName>"
                + "</_>"
                + "<" + domainName + ">"
                + "<credential>"
                + "<description>" + credentialDescription + "</description>"
                + "<displayName>" + credentialUsername + "/****** (" + credentialDescription + ")</displayName>"
                + "<fullName>system/" + domainName + "/" + credentialId + "</fullName>"
                + "<id>" + credentialId + "</id>"
                + "<typeName>Username with password</typeName>"
                + "</credential>"
                + "<description>"
                + domainDescription
                + "</description>"
                + "<displayName>" + domainName + "</displayName>"
                + "<fullDisplayName>System » " + domainName + "</fullDisplayName>"
                + "<fullName>system/" + domainName + "</fullName>"
                + "<global>false</global>"
                + "<urlName>" + domainName + "</urlName>"
                + "</" + domainName + ">"
                + "</domains>"
                + "</userFacingAction>").ignoreComments().ignoreWhitespace());
    }

    @Test
    public void restCRUDSmokes() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        j.getInstance().setCrumbIssuer(null);
        assertThat(systemStore.getDomainByName("smokes"), nullValue());
        // create domain
        HttpURLConnection con =
                postCreateByXml(systemStore, "<com.cloudbees.plugins.credentials.domains.Domain>\n"
                        + "  <name>smokes</name>\n"
                        + "</com.cloudbees.plugins.credentials.domains.Domain>");
        assertThat(con.getResponseCode(), is(200));
        assertThat(Items.XSTREAM2.toXML(systemStore.getDomainByName("smokes")), CompareMatcher.isIdenticalTo(
                "<com.cloudbees.plugins.credentials.domains.Domain>\n"
                        + "  <name>smokes</name>\n"
                        + "</com.cloudbees.plugins.credentials.domains.Domain>").ignoreWhitespace().ignoreComments());

        // read domain
        WebResponse response = wc.goTo("credentials/store/system/domain/smokes/config.xml", "application/xml").getWebResponse();
        assertThat(response.getContentAsString(), CompareMatcher.isIdenticalTo(
                "<com.cloudbees.plugins.credentials.domains.Domain>\n"
                        + "  <name>smokes</name>\n"
                        + "</com.cloudbees.plugins.credentials.domains.Domain>").ignoreWhitespace().ignoreComments());

        // update domain
        con = postConfigDotXml(systemStore, "smokes", "<com.cloudbees.plugins.credentials.domains.Domain>\n"
                        + "  <name>smokes</name>\n"
                + "<specifications>\n"
                + "<com.cloudbees.plugins.credentials.domains.HostnameSpecification>\n"
                + "<includes>smokes.example.com</includes>\n"
                + "<excludes/>\n"
                + "</com.cloudbees.plugins.credentials.domains.HostnameSpecification>\n"
                + "</specifications>\n"
                        + "</com.cloudbees.plugins.credentials.domains.Domain>");
        assertThat(con.getResponseCode(), is(200));
        assertThat(Items.XSTREAM2.toXML(systemStore.getDomainByName("smokes")), CompareMatcher.isIdenticalTo(
                "<com.cloudbees.plugins.credentials.domains.Domain>\n"
                        + "  <name>smokes</name>\n"
                        + "<specifications>\n"
                        + "<com.cloudbees.plugins.credentials.domains.HostnameSpecification>\n"
                        + "<includes>smokes.example.com</includes>\n"
                        + "<excludes/>\n"
                        + "</com.cloudbees.plugins.credentials.domains.HostnameSpecification>\n"
                        + "</specifications>\n"
                        + "</com.cloudbees.plugins.credentials.domains.Domain>").ignoreWhitespace().ignoreComments());

        // create credential
        con = postCreateByXml(systemStore, "smokes",
                "<com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                        + "  <scope>GLOBAL</scope>\n"
                        + "  <id>smokey-id</id>\n"
                        + "  <description>created from xml</description>\n"
                        + "  <username>example-com-deployer</username>\n"
                        + "  <usernameSecret>false</usernameSecret>\n"
                        + "  <password>super-secret</password>\n"
                        + "</com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>");
        assertThat(con.getResponseCode(), is(200));
        List<Credentials> credentials = systemStore.getCredentials(systemStore.getDomainByName("smokes"));
        assertThat(credentials, notNullValue());
        Credentials cred = credentials.isEmpty() ? null : credentials.get(0);
        assertThat(cred, instanceOf(UsernamePasswordCredentialsImpl.class));
        UsernamePasswordCredentialsImpl c = (UsernamePasswordCredentialsImpl) cred;
        assertThat(c.getScope(), is(CredentialsScope.GLOBAL));
        assertThat(c.getId(), is("smokey-id"));
        assertThat(c.getDescription(), is("created from xml"));
        assertThat(c.getUsername(), is("example-com-deployer"));
        assertThat(c.getPassword().getPlainText(), is("super-secret"));

        // read credential
        response = wc.goTo("credentials/store/system/domain/smokes/credential/smokey-id/config.xml", "application/xml").getWebResponse();
        assertThat(response.getContentAsString(), CompareMatcher.isIdenticalTo(
                "<com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                        + "  <scope>GLOBAL</scope>\n"
                        + "  <id>smokey-id</id>\n"
                        + "  <description>created from xml</description>\n"
                        + "  <username>example-com-deployer</username>\n"
                        + "  <password><secret-redacted/></password>\n"
                        + "  <usernameSecret>false</usernameSecret>\n"
                        + "</com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>").ignoreWhitespace().ignoreComments());

        // update credentials
        con = postConfigDotXml(systemStore, "smokes", "smokey-id",
                "<com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                        + "  <scope>SYSTEM</scope>\n"
                        + "  <id>smokey-id</id>\n"
                        + "  <description>updated by xml</description>\n"
                        + "  <username>example-org-deployer</username>\n"
                        + "  <usernameSecret>false</usernameSecret>\n"
                        + "  <password>super-duper-secret</password>\n"
                        + "</com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>");
        assertThat(con.getResponseCode(), is(200));
        credentials = systemStore.getCredentials(systemStore.getDomainByName("smokes"));
        assertThat(credentials, notNullValue());
        cred = credentials.isEmpty() ? null : credentials.get(0);
        assertThat(cred, instanceOf(UsernamePasswordCredentialsImpl.class));
        c = (UsernamePasswordCredentialsImpl) cred;
        assertThat(c.getScope(), is(CredentialsScope.SYSTEM));
        assertThat(c.getId(), is("smokey-id"));
        assertThat(c.getDescription(), is("updated by xml"));
        assertThat(c.getUsername(), is("example-org-deployer"));
        assertThat(c.getPassword().getPlainText(), is("super-duper-secret"));

        // delete credentials
        con = deleteConfigDotXml(systemStore, "smokes", "smokey-id");
        assertThat(con.getResponseCode(), is(200));
        assertThat(systemStore.getCredentials(systemStore.getDomainByName("smokes")), is(Collections.<Credentials>emptyList()));

        // delete domain
        con = deleteConfigDotXml(systemStore, "smokes");
        assertThat(con.getResponseCode(), is(200));
        assertThat(systemStore.getDomainByName("smokes"), nullValue());
    }

    @Test
    public void restCRUDNonHappy() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        j.getInstance().setCrumbIssuer(null);
        assertThat(systemStore.getDomainByName("smokes"), nullValue());
        // create domain
        HttpURLConnection con =
                postCreateByXml(systemStore, "<com.cloudbees.plugins.credentials.domains.Domain>\n"
                        + "  <name>smokes</name>\n"
                        + "</com.cloudbees.plugins.credentials.domains.Domain>");
        assumeThat(con.getResponseCode(), is(200));
        con = postCreateByXml(systemStore, "<com.cloudbees.plugins.credentials.domains.Domain>\n"
                        + "  <name>smokes</name>\n"
                        + "</com.cloudbees.plugins.credentials.domains.Domain>");
        assertThat(con.getResponseCode(), is(HttpServletResponse.SC_CONFLICT));

        // update domain
        con = postConfigDotXml(systemStore, "no-smokes", "<com.cloudbees.plugins.credentials.domains.Domain>\n"
                        + "  <name>no-smokes</name>\n"
                + "<specifications>\n"
                + "<com.cloudbees.plugins.credentials.domains.HostnameSpecification>\n"
                + "<includes>smokes.example.com</includes>\n"
                + "<excludes/>\n"
                + "</com.cloudbees.plugins.credentials.domains.HostnameSpecification>\n"
                + "</specifications>\n"
                        + "</com.cloudbees.plugins.credentials.domains.Domain>");
        assertThat(con.getResponseCode(), is(404));

        // create credential
        con = postCreateByXml(systemStore, "smokes",
                "<com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                        + "  <scope>GLOBAL</scope>\n"
                        + "  <id>smokey-id</id>\n"
                        + "  <description>created from xml</description>\n"
                        + "  <username>example-com-deployer</username>\n"
                        + "  <password>super-secret</password>\n"
                        + "</com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>");
        assumeThat(con.getResponseCode(), is(200));
        con = postCreateByXml(systemStore, "smokes",
                "<com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                        + "  <scope>GLOBAL</scope>\n"
                        + "  <id>smokey-id</id>\n"
                        + "  <description>created from xml</description>\n"
                        + "  <username>example-com-deployer</username>\n"
                        + "  <password>super-secret</password>\n"
                        + "</com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>");
        assertThat(con.getResponseCode(), is(HttpServletResponse.SC_CONFLICT));
    }

    private HttpURLConnection postCreateByXml(CredentialsStore store, String xml)
            throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(j.getURL(),
                "credentials/store/" + store.getStoreAction().getUrlName() + "/createDomain").openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/xml;charset=utf-8");
        con.setDoOutput(true);
        con.getOutputStream().write(xml.getBytes(StandardCharsets.UTF_8));
        return con;
    }

    private HttpURLConnection postCreateByXml(CredentialsStore store, String domainName, String xml)
            throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(j.getURL(),
                "credentials/store/" + store.getStoreAction().getUrlName() + "/domain/" + Util
                        .rawEncode(StringUtils.defaultIfBlank(domainName, "_")) + "/createCredentials")
                .openConnection();

        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/xml;charset=utf-8");
        con.setDoOutput(true);
        con.getOutputStream().write(xml.getBytes(StandardCharsets.UTF_8));
        return con;
    }

    private HttpURLConnection postConfigDotXml(CredentialsStore store, String domainName, String xml)
            throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(j.getURL(),
                "credentials/store/" + store.getStoreAction().getUrlName() + "/domain/" + Util
                        .rawEncode(StringUtils.defaultIfBlank(domainName, "_")) + "/config.xml").openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/xml;charset=utf-8");
        con.setDoOutput(true);
        con.getOutputStream().write(xml.getBytes(StandardCharsets.UTF_8));
        return con;
    }

    private HttpURLConnection postConfigDotXml(CredentialsStore store, String domainName, String credentialsId,
                                               String xml) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(j.getURL(),
                "credentials/store/" + store.getStoreAction().getUrlName() + "/domain/" + Util
                        .rawEncode(StringUtils.defaultIfBlank(domainName, "_")) + "/credential/" + Util
                        .rawEncode(credentialsId) + "/config.xml").openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/xml;charset=utf-8");
        con.setDoOutput(true);
        con.getOutputStream().write(xml.getBytes(StandardCharsets.UTF_8));
        return con;
    }

    private HttpURLConnection deleteConfigDotXml(CredentialsStore store, String domainName)
            throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(j.getURL(),
                "credentials/store/" + store.getStoreAction().getUrlName() + "/domain/" + Util
                        .rawEncode(StringUtils.defaultIfBlank(domainName, "_")) + "/config.xml").openConnection();
        con.setRequestMethod("DELETE");
        return con;
    }

    private HttpURLConnection deleteConfigDotXml(CredentialsStore store, String domainName, String credentialsId)
            throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(j.getURL(),
                "credentials/store/" + store.getStoreAction().getUrlName() + "/domain/" + Util
                        .rawEncode(StringUtils.defaultIfBlank(domainName, "_")) + "/credential/" + Util
                        .rawEncode(credentialsId) + "/config.xml").openConnection();
        con.setRequestMethod("DELETE");
        return con;
    }

}
