package com.cloudbees.plugins.credentials;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImplTest;

import hudson.model.UnprotectedRootAction;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;
import org.htmlunit.Page;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.DomNodeList;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlElementUtil;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlFormUtil;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlOption;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlRadioButtonInput;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class CredentialsSelectHelperTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private String pemCert;
    private String pemKey;

    private static final String VALID_PASSWORD = "password";
    private static final String INVALID_PASSWORD = "bla";
    
    @Before
    public void setup() throws IOException {
        pemCert = IOUtils.toString(CertificateCredentialsImplTest.class.getResource("certs.pem"),
                                   StandardCharsets.UTF_8);
        pemKey = IOUtils.toString(CertificateCredentialsImplTest.class.getResource("key.pem"),
                                  StandardCharsets.UTF_8);
    }


    @Test
    public void doAddCredentialsFromPopupWorksAsExpected() throws Exception {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage htmlPage = wc.goTo("credentials-selection");

            HtmlButton addCredentialsButton = htmlPage.querySelector(".credentials-add-menu");
            // The 'click' event doesn't fire a 'mouseenter' event causing the menu not to show, so let's fire one
            addCredentialsButton.fireEvent("mouseenter");
            addCredentialsButton.click();

            HtmlButton jenkinsCredentialsOption = htmlPage.querySelector(".jenkins-dropdown__item");
            jenkinsCredentialsOption.click();

            wc.waitForBackgroundJavaScript(4000);
            HtmlForm form = htmlPage.querySelector("#credentials-dialog-form");

            HtmlInput username = form.querySelector("input[name='_.username']");
            username.setValue("bob");
            HtmlInput password = form.querySelector("input[name='_.password']");
            password.setValue("secret");
            HtmlInput id = form.querySelector("input[name='_.id']");
            id.setValue("test");

            HtmlButton formSubmitButton = htmlPage.querySelector(".jenkins-button[data-id='ok']");
            formSubmitButton.fireEvent("click");
            wc.waitForBackgroundJavaScript(5000);

            // check if credentials were added
            List<UsernamePasswordCredentials> creds = CredentialsProvider.lookupCredentials(UsernamePasswordCredentials.class);
            assertThat(creds, Matchers.hasSize(1));
            UsernamePasswordCredentials cred = creds.get(0);
            assertThat(cred.getUsername(), is("bob"));
            assertThat(cred.getPassword().getPlainText(), is("secret"));
        }
    }

    @Test
    @Issue("JENKINS-74964")
    public void doAddCredentialsFromPopupForPEMCertificateKeystore() throws Exception {

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage htmlPage = wc.goTo("credentials-selection");
            HtmlForm form = selectPEMCertificateKeyStore(htmlPage, wc);
            form.getTextAreaByName("_.certChain").setTextContent(pemCert);
            form.getTextAreaByName("_.privateKey").setTextContent(pemKey);
            form.getInputsByName("_.password").forEach(input -> input.setValue(VALID_PASSWORD));
            Page submit = HtmlFormUtil.submit(form);
            JSONObject responseJson = JSONObject.fromObject(submit.getWebResponse().getContentAsString());
            assertThat(responseJson.getString("notificationType"), is("SUCCESS"));
        }
    }

    @Test
    @Issue("JENKINS-74964")
    public void doAddCredentialsFromPopupForPEMCertificateKeystore_missingKeyStore() throws Exception {

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage htmlPage = wc.goTo("credentials-selection");
            HtmlForm form = selectPEMCertificateKeyStore(htmlPage, wc);
            Page submit = HtmlFormUtil.submit(form);
            JSONObject responseJson = JSONObject.fromObject(submit.getWebResponse().getContentAsString());
            assertThat(responseJson.getString("notificationType"), is("ERROR"));
        }
    }

    @Test
    @Issue("JENKINS-74964")
    public void doAddCredentialsFromPopupForInvalidPEMCertificateKeystore_missingCert() throws Exception {

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage htmlPage = wc.goTo("credentials-selection");
            HtmlForm form = selectPEMCertificateKeyStore(htmlPage, wc);
            form.getTextAreaByName("_.certChain").setTextContent(null);
            form.getTextAreaByName("_.privateKey").setTextContent(pemKey);
            form.getInputsByName("_.password").forEach(input -> input.setValue(VALID_PASSWORD));
            Page submit = HtmlFormUtil.submit(form);
            JSONObject responseJson = JSONObject.fromObject(submit.getWebResponse().getContentAsString());
            assertThat(responseJson.getString("notificationType"), is("ERROR"));
        }
    }

    @Test
    @Issue("JENKINS-74964")
    public void doAddCredentialsFromPopupForInvalidPEMCertificateKeystore_missingPassword() throws Exception {

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage htmlPage = wc.goTo("credentials-selection");
            HtmlForm form = selectPEMCertificateKeyStore(htmlPage, wc);
            form.getTextAreaByName("_.certChain").setTextContent(pemCert);
            form.getTextAreaByName("_.privateKey").setTextContent(pemKey);
            Page submit = HtmlFormUtil.submit(form);
            JSONObject responseJson = JSONObject.fromObject(submit.getWebResponse().getContentAsString());
            assertThat(responseJson.getString("notificationType"), is("ERROR"));
        }
    }

    @Test
    @Issue("JENKINS-74964")
    public void doAddCredentialsFromPopupForInvalidPEMCertificateKeystore_invalidPassword() throws Exception {

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage htmlPage = wc.goTo("credentials-selection");
            HtmlForm form = selectPEMCertificateKeyStore(htmlPage, wc);
            form.getTextAreaByName("_.certChain").setTextContent(pemCert);
            form.getTextAreaByName("_.privateKey").setTextContent(pemKey);
            form.getInputsByName("_.password").forEach(input -> input.setValue(INVALID_PASSWORD));
            Page submit = HtmlFormUtil.submit(form);
            JSONObject responseJson = JSONObject.fromObject(submit.getWebResponse().getContentAsString());
            assertThat(responseJson.getString("notificationType"), is("ERROR"));
        }
    }

    private HtmlForm selectPEMCertificateKeyStore(HtmlPage htmlPage, JenkinsRule.WebClient wc) throws IOException {
        HtmlButton addCredentialsButton = htmlPage.querySelector(".credentials-add-menu");
        addCredentialsButton.fireEvent("mouseenter");
        addCredentialsButton.click();

        HtmlButton jenkinsCredentialsOption = htmlPage.querySelector(".jenkins-dropdown__item");
        jenkinsCredentialsOption.click();

        wc.waitForBackgroundJavaScript(4000);
        HtmlForm form = htmlPage.querySelector("#credentials-dialog-form");
        String certificateDisplayName = j.jenkins.getDescriptor(CertificateCredentialsImpl.class).getDisplayName();
        String KeyStoreSourceDisplayName = j.jenkins.getDescriptor(
                CertificateCredentialsImpl.PEMEntryKeyStoreSource.class).getDisplayName();
        DomNodeList<DomNode> allOptions = htmlPage.getDocumentElement().querySelectorAll(
                "select.dropdownList option");
        boolean optionFound = selectOption(allOptions, certificateDisplayName);
        assertTrue("The Certificate option was not found in the credentials type select", optionFound);
        List<HtmlRadioButtonInput> inputs = htmlPage.getDocumentElement().getByXPath(
                "//input[contains(@name, 'keyStoreSource') and following-sibling::label[contains(.,'"
                + KeyStoreSourceDisplayName + "')]]");
        assertThat("query should return only a singular input", inputs, hasSize(1));
        HtmlElementUtil.click(inputs.get(0));
        wc.waitForBackgroundJavaScript(4000);
        return form;
    }

    private static boolean selectOption(DomNodeList<DomNode> allOptions, String optionName) {
        return allOptions.stream().anyMatch(domNode -> {
            if (domNode instanceof HtmlOption option) {
                if (option.getVisibleText().equals(optionName)) {
                    try {
                        HtmlElementUtil.click(option);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return true;
                }
            }

            return false;
        });
    }

    @TestExtension
    public static class CredentialsSelectionAction implements UnprotectedRootAction {
        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return "credentials-selection";
        }
    }
}
