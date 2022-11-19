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
import com.gargoylesoftware.htmlunit.FormEncodingType;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlElementUtil;
import com.gargoylesoftware.htmlunit.html.HtmlFileInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import hudson.FilePath;
import hudson.Util;
import hudson.cli.CLICommandInvoker;
import hudson.cli.UpdateJobCommand;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Slave;
import hudson.security.ACL;
import hudson.slaves.CommandLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.GroovyLanguage;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javaposse.jobdsl.plugin.GlobalJobDslSecurityConfiguration;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.FileOutputStream;
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
import static org.junit.Assume.assumeThat;

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

    // See setupAgent() below
    @Rule
    public TemporaryFolder tmpAgent = new TemporaryFolder();
    @Rule
    public TemporaryFolder tmpWorker = new TemporaryFolder();
    // Where did we save that file?..
    private File agentJar = null;
    // Can this be reused for many test cases?
    private Slave agent = null;
    // Unknown/started/not usable
    private Boolean agentUsable = null;

    @Before
    public void setup() throws IOException {
        p12 = tmp.newFile("test.p12");
        FileUtils.copyURLToFile(CertificateCredentialsImplTest.class.getResource("test.p12"), p12);
        p12Invalid = tmp.newFile("invalid.p12");
        FileUtils.copyURLToFile(CertificateCredentialsImplTest.class.getResource("invalid.p12"), p12Invalid);

        r.jenkins.setCrumbIssuer(null);
    }

    // Helpers for some of the test cases (initially tied to JENKINS-70101 research)
    // TODO: Offload to some class many tests can call upon?
    Boolean isAvailableAgent() {
        // Can be used to skip optional tests if we know we could not set up an agent
        if (agentJar == null)
            return false;
        if (agent == null)
            return false;
        return agentUsable;
    }

    Boolean setupAgent() throws IOException, InterruptedException, OutOfMemoryError {
        // Note we anticipate this might fail; it should not block the whole test suite from running
        // Loosely inspired by
        // https://docs.cloudbees.com/docs/cloudbees-ci-kb/latest/client-and-managed-masters/create-agent-node-from-groovy

        // Is it known-impossible to start the agent?
        if (agentUsable != null && agentUsable == false)
            return agentUsable; // quickly for re-runs

        // Did we download this file for earlier test cases?
        if (agentJar == null) {
            try {
                URL url = new URL(r.jenkins.getRootUrl() + "jnlpJars/agent.jar");
                agentJar = tmpAgent.newFile("agent.jar");
                FileOutputStream out = new FileOutputStream(agentJar);
                out.write(url.openStream().readAllBytes());
                out.close();
            } catch (IOException | OutOfMemoryError e) {
                agentJar = null;
                agentUsable = false;

                System.out.println("Failed to download agent.jar from test instance: " +
                    e.toString());

                return agentUsable;
            }
        }

        // This CLI spelling and quoting should play well with both Windows
        // (including spaces in directory names) and Unix/Linux
        ComputerLauncher launcher = new CommandLauncher(
            "\"" + System.getProperty("java.home") + File.separator + "bin" +
            File.separator + "java\" -jar \"" + agentJar.getAbsolutePath().toString() + "\""
        );

        try {
            // Define a "Permanent Agent"
            agent = new DumbSlave(
                    "worker",
                    tmpWorker.getRoot().getAbsolutePath().toString(),
                    launcher);
            agent.setNodeDescription("Worker in another JVM, remoting used");
            agent.setNumExecutors(1);
            agent.setLabelString("worker");
            agent.setMode(Node.Mode.EXCLUSIVE);
            agent.setRetentionStrategy(new RetentionStrategy.Always());

/*
            // Add node envvars
            List<Entry> env = new ArrayList<Entry>();
            env.add(new Entry("key1","value1"));
            env.add(new Entry("key2","value2"));
            EnvironmentVariablesNodeProperty envPro = new EnvironmentVariablesNodeProperty(env);
            agent.getNodeProperties().add(envPro);
*/

            r.jenkins.addNode(agent);

            String agentLog = null;
            agentUsable = false;
            for (long i = 0; i < 5; i++) {
                Thread.sleep(1000);
                agentLog = agent.getComputer().getLog();
                if (i == 2 && (agentLog == null || agentLog.isEmpty())) {
                    // Give it a little time to autostart, then kick it up if needed:
                    agent.getComputer().connect(true); // "always" should have started it; avoid duplicate runs
                }
                if (agentLog != null && agentLog.contains("Agent successfully connected and online")) {
                    agentUsable = true;
                    break;
                }
            }
            System.out.println("Spawned build agent " +
                "usability: " + agentUsable.toString() +
                "; connection log:" + (agentLog == null ? " <null>" : "\n" + agentLog));
        } catch (Descriptor.FormException | NullPointerException e) {
            agentUsable = false;
        }

        return agentUsable;
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
        newCredentialsForm.getInputsByName("_.password").forEach(input -> input.setValueAttribute(VALID_PASSWORD));
        htmlPage.getDocumentElement().querySelector("input[type=file][name=uploadedCertFile]");
        
        List<CertificateCredentials> certificateCredentials = CredentialsProvider.lookupCredentials(CertificateCredentials.class, (ItemGroup<?>) null, ACL.SYSTEM);
        assertThat(certificateCredentials, hasSize(0));
        
        r.submit(newCredentialsForm);

        certificateCredentials = CredentialsProvider.lookupCredentials(CertificateCredentials.class, (ItemGroup<?>) null, ACL.SYSTEM);
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

    // Helper for a few tests below
    // Roughly follows what tests above were proven to succeed doing
    private void prepareUploadedKeystore() throws IOException {
        prepareUploadedKeystore("myCert", "password");
    }

    private void prepareUploadedKeystore(String id, String password) throws IOException {
        SecretBytes uploadedKeystore = SecretBytes.fromBytes(Files.readAllBytes(p12.toPath()));
        CertificateCredentialsImpl.UploadedKeyStoreSource storeSource = new CertificateCredentialsImpl.UploadedKeyStoreSource(uploadedKeystore);
        CertificateCredentialsImpl credentials = new CertificateCredentialsImpl(null, id, null, password, storeSource);
        SystemCredentialsProvider.getInstance().getCredentials().add(credentials);
        SystemCredentialsProvider.getInstance().save();
    }

    String cpsScriptCredentialTestImports() {
        return  "import com.cloudbees.plugins.credentials.CredentialsMatchers;\n" +
                "import com.cloudbees.plugins.credentials.CredentialsProvider;\n" +
                "import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;\n" +
                "import com.cloudbees.plugins.credentials.common.StandardCredentials;\n" +
                "import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;\n" +
                "import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;\n" +
                "import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;\n" +
                "import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl.KeyStoreSource;\n" +
                "import hudson.security.ACL;\n" +
                "import java.security.KeyStore;\n" +
                "\n";
    }

    String cpsScriptCredentialTest(String runnerTag) {
        return cpsScriptCredentialTest("myCert", "password", runnerTag);
    }

    String cpsScriptCredentialTest(String id, String password, String runnerTag) {
        return  "def authentication='" + id + "';\n" +
                "def password='" + password + "';\n" +
                "StandardCredentials credential = CredentialsMatchers.firstOrNull(\n" +
                "    CredentialsProvider.lookupCredentials(\n" +
                "        StandardCredentials.class,\n" +
                "        Jenkins.instance, null, null),\n" +
                "    CredentialsMatchers.withId(authentication));\n" +
                "StandardCredentials credentialSnap = CredentialsProvider.snapshot(credential);\n\n" +
                "\n" +
                "echo \"CRED ON " + runnerTag + ":\"\n" +
                "echo credential.toString()\n" +
                "KeyStore keyStore = credential.getKeyStore();\n" +
                "KeyStoreSource kss = ((CertificateCredentialsImpl) credential).getKeyStoreSource();\n" +
                "echo \"KSS: \" + kss.toString()\n" +
                "byte[] kssb = kss.getKeyStoreBytes();\n" +
                "echo \"KSS bytes (len): \" + kssb.length\n" +
                "\n" +
                "echo \"CRED-SNAP ON " + runnerTag + ":\"\n" +
                "echo credentialSnap.toString()\n" +
                "KeyStore keyStoreSnap = credentialSnap.getKeyStore();\n" +
                "KeyStoreSource kssSnap = ((CertificateCredentialsImpl) credentialSnap).getKeyStoreSource();\n" +
                "echo \"KSS-SNAP: \" + kssSnap.toString()\n" +
                "byte[] kssbSnap = kssSnap.getKeyStoreBytes();\n" +
                "echo \"KSS-SNAP bytes (len): \" + kssbSnap.length\n" +
                "\n";
    }

    private void relaxScriptSecurityScript(String script) throws IOException {
        ScriptApproval.get().preapprove(script, GroovyLanguage.get());
        for (ScriptApproval.PendingScript p : ScriptApproval.get().getPendingScripts()) {
            ScriptApproval.get().approveScript(p.getHash());
        }
    }

    private void relaxScriptSecurityGlobal() throws IOException {
        StaplerRequest stapler = null;
        net.sf.json.JSONObject jsonObject = new net.sf.json.JSONObject();
        jsonObject.put("useScriptSecurity", false);
        GlobalConfiguration.all().get(GlobalJobDslSecurityConfiguration.class).configure(stapler, jsonObject);
        GlobalConfiguration.all().get(GlobalJobDslSecurityConfiguration.class).save();
/*
        GlobalConfiguration.all().get(GlobalJobDslSecurityConfiguration.class).useScriptSecurity=false;
        GlobalConfiguration.all().get(GlobalJobDslSecurityConfiguration.class).save();
 */
    }

    private void relaxScriptSecurityCredentialTestSignatures() throws IOException {
        ScriptApproval.get().approveSignature("method com.cloudbees.plugins.credentials.common.CertificateCredentials getKeyStore");
        ScriptApproval.get().approveSignature("method com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl getKeyStoreSource");
        ScriptApproval.get().approveSignature("method com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl$KeyStoreSource getKeyStoreBytes");
        ScriptApproval.get().approveSignature("staticMethod com.cloudbees.plugins.credentials.CredentialsMatchers firstOrNull java.lang.Iterable com.cloudbees.plugins.credentials.CredentialsMatcher");
        ScriptApproval.get().approveSignature("staticMethod com.cloudbees.plugins.credentials.CredentialsMatchers withId java.lang.String");
        ScriptApproval.get().approveSignature("staticMethod com.cloudbees.plugins.credentials.CredentialsProvider lookupCredentials java.lang.Class hudson.model.ItemGroup org.acegisecurity.Authentication java.util.List");
        ScriptApproval.get().approveSignature("staticMethod com.cloudbees.plugins.credentials.CredentialsProvider snapshot com.cloudbees.plugins.credentials.Credentials");
        ScriptApproval.get().approveSignature("staticMethod jenkins.model.Jenkins getInstance");
    }

    @Test
    @Issue("JENKINS-70101")
    public void keyStoreReadableOnController() throws Exception {
        // Check that credentials are usable with pipeline script
        // running without a node{}
        prepareUploadedKeystore();

        // Configure the build to use the credential
        WorkflowJob proj = r.jenkins.createProject(WorkflowJob.class, "proj");
        String script = cpsScriptCredentialTestImports() +
                cpsScriptCredentialTest("CONTROLLER BUILT-IN");
        proj.setDefinition(new CpsFlowDefinition(script, true));
        relaxScriptSecurityCredentialTestSignatures();

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        r.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        r.assertLogContains("KSS-SNAP bytes", run);
    }

    @Test
    @Issue("JENKINS-70101")
    public void keyStoreReadableOnNodeLocal() throws Exception {
        // Check that credentials are usable with pipeline script
        // running on a node{} (provided by the controller)
        prepareUploadedKeystore();

        // Configure the build to use the credential
        WorkflowJob proj = r.jenkins.createProject(WorkflowJob.class, "proj");
        String script = cpsScriptCredentialTestImports() +
                "node {\n" +
                cpsScriptCredentialTest("CONTROLLER NODE") +
                "}\n";
        proj.setDefinition(new CpsFlowDefinition(script, true));
        relaxScriptSecurityCredentialTestSignatures();

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        r.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        r.assertLogContains("KSS-SNAP bytes", run);
    }

    @Test
    @Issue("JENKINS-70101")
    public void keyStoreReadableOnNodeRemote() throws Exception {
        // Check that credentials are usable with pipeline script
        // running on a remote node{} with separate JVM (check
        // that remoting/snapshot work properly)
        assumeThat("This test needs a separate build agent", this.setupAgent(), is(true));

        prepareUploadedKeystore();

        // Configure the build to use the credential
        WorkflowJob proj = r.jenkins.createProject(WorkflowJob.class, "proj");
        String script = cpsScriptCredentialTestImports() +
                "node(\"worker\") {\n" +
                cpsScriptCredentialTest("REMOTE NODE") +
                "}\n";
        proj.setDefinition(new CpsFlowDefinition(script, true));
        relaxScriptSecurityCredentialTestSignatures();

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        r.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        r.assertLogContains("KSS-SNAP bytes", run);
    }
}
