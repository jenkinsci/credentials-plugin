/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package com.cloudbees.plugins.credentials.impl;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.CertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import org.htmlunit.FormEncodingType;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.DomNodeList;
import org.htmlunit.html.HtmlElementUtil;
import org.htmlunit.html.HtmlFileInput;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlOption;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlRadioButtonInput;
import hudson.FilePath;
import hudson.Util;
import hudson.cli.CLICommandInvoker;
import hudson.cli.UpdateJobCommand;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.security.ACL;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class CertificateCredentialsImplTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File p12;
    private File p12Invalid;

    private static final String VALID_PASSWORD = "password";
    private static final String INVALID_PASSWORD = "blabla";
    private static final String EXPECTED_DISPLAY_NAME = "EMAILADDRESS=me@myhost.mydomain, CN=pkcs12, O=Fort-Funston, L=SanFrancisco, ST=CA, C=US";

    @Before
    public void setup() throws IOException {
        p12 = tmp.newFile("test.p12");
        FileUtils.copyURLToFile(CertificateCredentialsImplTest.class.getResource("test.p12"), p12);
        p12Invalid = tmp.newFile("invalid.p12");
        FileUtils.copyURLToFile(CertificateCredentialsImplTest.class.getResource("invalid.p12"), p12Invalid);

        r.jenkins.setCrumbIssuer(null);
    }

    @Test
    public void displayName() throws IOException {
        SecretBytes uploadedKeystore = SecretBytes.fromBytes(Files.readAllBytes(p12.toPath()));
        CertificateCredentialsImpl.UploadedKeyStoreSource storeSource = new CertificateCredentialsImpl.UploadedKeyStoreSource(uploadedKeystore);
        assertEquals(EXPECTED_DISPLAY_NAME, CredentialsNameProvider.name(new CertificateCredentialsImpl(null, "abc123", null, "password", storeSource)));
    }

    @Test
    @Issue("SECURITY-1322")
    public void verifySystemMasterSourceConvertedToUploadedSource() throws Exception {
        CertificateCredentialsImpl.FileOnMasterKeyStoreSource storeSource = new CertificateCredentialsImpl.FileOnMasterKeyStoreSource(p12.getAbsolutePath());
        CertificateCredentialsImpl credentials = new CertificateCredentialsImpl(null, "abc123", null, "password", storeSource);
        SystemCredentialsProvider.getInstance().getCredentials().add(credentials);
        SystemCredentialsProvider.getInstance().save();
        SystemCredentialsProvider provider = new SystemCredentialsProvider();
        Credentials reloadedCredentials = provider.getCredentials().get(0);
        assertThat(reloadedCredentials, instanceOf(CertificateCredentials.class));
        CertificateCredentialsImpl.KeyStoreSource reloadedSource = ((CertificateCredentialsImpl) reloadedCredentials).getKeyStoreSource();
        assertThat(reloadedSource, instanceOf(CertificateCredentialsImpl.UploadedKeyStoreSource.class));
        provider.save();
        FilePath credentialsXml = r.getInstance().getRootPath().child("credentials.xml");
        String fileContents = credentialsXml.readToString();
        assertThat(fileContents, not(containsString("Master")));
    }

    @Test
    @Issue("SECURITY-1322")
    public void verifyGlobalMasterSourceConvertedToUploadedSource() throws Exception {
        Folder folder = r.jenkins.createProject(Folder.class, "folder1");
        CertificateCredentialsImpl.FileOnMasterKeyStoreSource storeSource = new CertificateCredentialsImpl.FileOnMasterKeyStoreSource(p12.getAbsolutePath());
        CertificateCredentialsImpl credentials = new CertificateCredentialsImpl(null, "abc123", null, "password", storeSource);
        CredentialsStore folderStore = getFolderStore(folder);
        Domain domain = new Domain("test", "test", Collections.emptyList());
        folderStore.addDomain(domain, credentials);
        folderStore.save();
        folder.doReload();
        CredentialsStore reloadedStore = getFolderStore(folder);
        List<Credentials> reloadedCredentialsList = reloadedStore.getCredentials(domain);
        assertThat(reloadedCredentialsList, hasSize(1));
        Credentials reloadedCredentials = reloadedCredentialsList.get(0);
        CertificateCredentialsImpl.KeyStoreSource reloadedSource = ((CertificateCredentialsImpl) reloadedCredentials).getKeyStoreSource();
        assertThat(reloadedSource, instanceOf(CertificateCredentialsImpl.UploadedKeyStoreSource.class));
        reloadedStore.save();
        String configFileContent = folder.getConfigFile().asString();
        assertThat(configFileContent, not(containsString("Master")));
    }

    @Test
    @Issue("SECURITY-1322")
    @LocalData("updateFolder")
    public void verifyUserWithoutRunScriptsCannotUploadMasterKeySource() throws Exception {
        Folder folder = r.jenkins.createProject(Folder.class, "folder1");

        FilePath updateFolder = r.jenkins.getRootPath().child("updateFolder.xml");
        CLICommandInvoker.Result result = new CLICommandInvoker(r, new UpdateJobCommand())
                .authorizedTo(Jenkins.READ, Job.READ, Job.CONFIGURE)
                .withStdin(updateFolder.read())
                .invokeWithArgs("folder1");

        assertThat(result.stderr(), containsString("user is missing the Overall/RunScripts permission"));
        // 1 = means An error occurred, according to https://github.com/jenkinsci/jenkins/pull/1997/files#diff-4459859ade69b51edffdb58020f5d3f7R217
        assertThat(result, failedWith(1));

        String configFileContent = folder.getConfigFile().asString();
        assertThat(configFileContent, not(containsString("Master")));
    }

    @Test
    @Issue("SECURITY-1322")
    @LocalData("updateFolder")
    public void verifyUserWithRunScriptsCanUploadMasterKeySource() throws Exception {
        Folder folder = r.jenkins.createProject(Folder.class, "folder1");

        FilePath updateFolder = r.jenkins.getRootPath().child("updateFolder.xml");
        CLICommandInvoker.Result result = new CLICommandInvoker(r, new UpdateJobCommand())
                .authorizedTo(Jenkins.ADMINISTER)
                .withStdin(updateFolder.read())
                .invokeWithArgs("folder1");

        assertThat(result, succeeded());

        Domain domain = new Domain("test", "test", Collections.emptyList());

        // check the data is correctly updated in memory
        CredentialsStore folderStore = getFolderStore(folder);
        List<Credentials> credentialsList = folderStore.getCredentials(domain);
        assertThat(credentialsList, hasSize(1));
        Credentials credentials = credentialsList.get(0);
        CertificateCredentialsImpl.KeyStoreSource source = ((CertificateCredentialsImpl) credentials).getKeyStoreSource();
        assertThat(source, instanceOf(CertificateCredentialsImpl.UploadedKeyStoreSource.class));

        folder.doReload();

        // as well as after a reload
        CredentialsStore reloadedFolderStore = getFolderStore(folder);
        List<Credentials> reloadedCredentialsList = reloadedFolderStore.getCredentials(domain);
        assertThat(reloadedCredentialsList, hasSize(1));
        Credentials reloadedCredentials = reloadedCredentialsList.get(0);
        CertificateCredentialsImpl.KeyStoreSource reloadedSource = ((CertificateCredentialsImpl) reloadedCredentials).getKeyStoreSource();
        assertThat(reloadedSource, instanceOf(CertificateCredentialsImpl.UploadedKeyStoreSource.class));
    }

    @Test
    @Issue("JENKINS-64542")
    public void doCheckUploadedKeystore_uploadedFileValid() throws Exception {
        String content = getContentFrom_doCheckUploadedKeystore("", getValidP12_base64(), VALID_PASSWORD);
        assertThat(content, containsString("ok"));
        assertThat(content, containsString(EXPECTED_DISPLAY_NAME));
    }

    @Test
    @Issue("JENKINS-64542")
    public void doCheckUploadedKeystore_uploadedFileValid_encryptedPassword() throws Exception {
        String content = getContentFrom_doCheckUploadedKeystore("", getValidP12_base64(), Secret.fromString(VALID_PASSWORD).getEncryptedValue());
        assertThat(content, containsString("ok"));
        assertThat(content, containsString(EXPECTED_DISPLAY_NAME));
    }

    @Test
    @Issue("JENKINS-64542")
    public void doCheckUploadedKeystore_uploadedFileValid_butMissingPassword() throws Exception {
        String content = getContentFrom_doCheckUploadedKeystore("", getValidP12_base64(), "");
        assertThat(content, containsString("warning"));
        assertThat(content, containsString(Util.escape(Messages.CertificateCredentialsImpl_LoadKeyFailedQueryEmptyPassword("1"))));
    }

    @Test
    @Issue("JENKINS-64542")
    public void doCheckUploadedKeystore_uploadedFileValid_butInvalidPassword() throws Exception {
        String content = getContentFrom_doCheckUploadedKeystore("", getValidP12_base64(), INVALID_PASSWORD);
        assertThat(content, containsString("warning"));
        assertThat(content, containsString(Util.escape(Messages.CertificateCredentialsImpl_LoadKeystoreFailed())));
    }

    @Test
    @Issue("JENKINS-64542")
    public void doCheckUploadedKeystore_uploadedFileInvalid() throws Exception {
        String content = getContentFrom_doCheckUploadedKeystore("", getInvalidP12_base64(), VALID_PASSWORD);
        assertThat(content, containsString("warning"));
        assertThat(content, containsString(Util.escape(Messages.CertificateCredentialsImpl_LoadKeystoreFailed())));
    }

    @Test
    @Issue("JENKINS-64542")
    public void doCheckUploadedKeystore_keyStoreBlank() throws Exception {
        String content = getContentFrom_doCheckUploadedKeystore("", "", VALID_PASSWORD);
        assertThat(content, containsString("error"));
        assertThat(content, containsString(Util.escape(Messages.CertificateCredentialsImpl_NoCertificateUploaded())));
    }

    @Test
    @Issue("JENKINS-64542")
    public void doCheckUploadedKeystore_keyStoreDefault() throws Exception {
        String content = getContentFrom_doCheckUploadedKeystore(CertificateCredentialsImpl.UploadedKeyStoreSource.DescriptorImpl.DEFAULT_VALUE, "", VALID_PASSWORD);
        assertThat(content, not(allOf(containsString("warning"), containsString("error"))));
    }

    @Test
    @Issue("JENKINS-64542")
    public void doCheckUploadedKeystore_keyStoreInvalidSecret() throws Exception {
        String content = getContentFrom_doCheckUploadedKeystore("", "", VALID_PASSWORD);
        assertThat(content, containsString("error"));
        assertThat(content, containsString(Util.escape(Messages.CertificateCredentialsImpl_NoCertificateUploaded())));
    }

    @Test
    @Issue("JENKINS-64542")
    public void doCheckUploadedKeystore_keyStoreValid() throws Exception {
        String content = getContentFrom_doCheckUploadedKeystore(getValidP12_secretBytes(), "", VALID_PASSWORD);
        assertThat(content, containsString("ok"));
        assertThat(content, containsString(EXPECTED_DISPLAY_NAME));
    }

    @Test
    @Issue("JENKINS-64542")
    public void doCheckUploadedKeystore_keyStoreValid_encryptedPassword() throws Exception {
        String content = getContentFrom_doCheckUploadedKeystore(getValidP12_secretBytes(), "", Secret.fromString(VALID_PASSWORD).getEncryptedValue());
        assertThat(content, containsString("ok"));
        assertThat(content, containsString(EXPECTED_DISPLAY_NAME));
    }

    @Test
    @Issue("JENKINS-64542")
    public void doCheckUploadedKeystore_keyStoreValid_butMissingPassword() throws Exception {
        String content = getContentFrom_doCheckUploadedKeystore(getValidP12_secretBytes(), "", "");
        assertThat(content, containsString("warning"));
        assertThat(content, containsString(Util.escape(Messages.CertificateCredentialsImpl_LoadKeyFailedQueryEmptyPassword("1"))));
    }

    @Test
    @Issue("JENKINS-64542")
    public void doCheckUploadedKeystore_keyStoreInvalid() throws Exception {
        String content = getContentFrom_doCheckUploadedKeystore(getInvalidP12_secretBytes(), "", VALID_PASSWORD);
        assertThat(content, containsString("warning"));
        assertThat(content, containsString(Util.escape(Messages.CertificateCredentialsImpl_LoadKeystoreFailed())));
    }

    @Test
    @Issue("JENKINS-63761")
    public void fullSubmitOfUploadedKeystore() throws Exception {
        String certificateDisplayName = r.jenkins.getDescriptor(CertificateCredentialsImpl.class).getDisplayName();
        
        JenkinsRule.WebClient wc = r.createWebClient();
        HtmlPage htmlPage = wc.goTo("credentials/store/system/domain/_/newCredentials");
        HtmlForm newCredentialsForm = htmlPage.getFormByName("newCredentials");

        DomNodeList<DomNode> allOptions = htmlPage.getDocumentElement().querySelectorAll("select.dropdownList option");
        boolean optionFound = allOptions.stream().anyMatch(domNode -> {
            if (domNode instanceof HtmlOption) {
                HtmlOption option = (HtmlOption) domNode;
                if (option.getVisibleText().equals(certificateDisplayName)) {
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
        assertTrue("The Certificate option was not found in the credentials type select", optionFound);
        
        HtmlRadioButtonInput keyStoreRadio = htmlPage.getDocumentElement().querySelector("input[name$=keyStoreSource]");
        HtmlElementUtil.click(keyStoreRadio);

        HtmlFileInput uploadedCertFileInput = htmlPage.getDocumentElement().querySelector("input[type=file][name=uploadedCertFile]");
        uploadedCertFileInput.setFiles(p12);

        // for all the types of credentials
        newCredentialsForm.getInputsByName("_.password").forEach(input -> input.setValue(VALID_PASSWORD));
        htmlPage.getDocumentElement().querySelector("input[type=file][name=uploadedCertFile]");
        
        List<CertificateCredentials> certificateCredentials = CredentialsProvider.lookupCredentialsInItemGroup(CertificateCredentials.class, (ItemGroup<?>) null, ACL.SYSTEM2);
        assertThat(certificateCredentials, hasSize(0));
        
        r.submit(newCredentialsForm);

        certificateCredentials = CredentialsProvider.lookupCredentialsInItemGroup(CertificateCredentials.class, (ItemGroup<?>) null, ACL.SYSTEM2);
        assertThat(certificateCredentials, hasSize(1));

        CertificateCredentials certificate = certificateCredentials.get(0);
        String displayName = StandardCertificateCredentials.NameProvider.getSubjectDN(certificate.getKeyStore());
        assertEquals(EXPECTED_DISPLAY_NAME, displayName);
    }

    private String getValidP12_base64() throws Exception {
        return Base64.getEncoder().encodeToString(Files.readAllBytes(p12.toPath()));
    }

    private String getValidP12_secretBytes() throws Exception {
        return SecretBytes.fromBytes(Files.readAllBytes(p12.toPath())).toString();
    }

    private String getInvalidP12_base64() throws Exception {
        return Base64.getEncoder().encodeToString(Files.readAllBytes(p12Invalid.toPath()));
    }

    private String getInvalidP12_secretBytes() throws Exception {
        return SecretBytes.fromBytes(Files.readAllBytes(p12Invalid.toPath())).toString();
    }

    private String getContentFrom_doCheckUploadedKeystore(String value, String uploadedCertFile, String password) throws Exception {
        String descriptorUrl = r.jenkins.getDescriptor(CertificateCredentialsImpl.UploadedKeyStoreSource.class).getDescriptorUrl();
        WebRequest request = new WebRequest(new URL(r.getURL() + descriptorUrl + "/checkUploadedKeystore"), HttpMethod.POST);
        request.setEncodingType(FormEncodingType.URL_ENCODED);
        request.setRequestBody(
                "value="+URLEncoder.encode(value, StandardCharsets.UTF_8.name())+
                        "&uploadedCertFile="+URLEncoder.encode(uploadedCertFile, StandardCharsets.UTF_8.name())+
                        "&password="+URLEncoder.encode(password, StandardCharsets.UTF_8.name())
        );

        JenkinsRule.WebClient wc = r.createWebClient();
        Page page = wc.getPage(request);

        return page.getWebResponse().getContentAsString();
    }

    private CredentialsStore getFolderStore(Folder f) {
        Iterable<CredentialsStore> stores = CredentialsProvider.lookupStores(f);
        CredentialsStore folderStore = null;
        for (CredentialsStore s : stores) {
            if (s.getProvider() instanceof FolderCredentialsProvider && s.getContext() == f) {
                folderStore = s;
                break;
            }
        }
        return folderStore;
    }

}
