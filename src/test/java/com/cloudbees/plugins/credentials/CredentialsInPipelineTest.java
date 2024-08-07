/*
 * The MIT License
 *
 * Copyright 2022 Jim Klimov.
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

import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImplTest;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Slave;
import hudson.slaves.CommandLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assume.assumeThat;

public class CredentialsInPipelineTest {
    /**
     * The CredentialsInPipelineTest suite prepares pipeline scripts to
     * retrieve some previously saved credentials, on the controller,
     * on a node provided by it, and on a worker agent in separate JVM.
     * This picks known-working test cases and their setup from other
     * test classes which address those credential types in more detail.
     * Initially tied to JENKINS-70101 research.
     */

    // For developers: set to `true` so that pipeline console logs show
    // up in System.out (and/or System.err) of the plugin test run by
    //   mvn test -Dtest="CredentialsInPipelineTest"
    private boolean verbosePipelines = false;

    @Rule
    public JenkinsRule r = new JenkinsRule();

    // Data for build agent setup
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

    // From CertificateCredentialImplTest
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    private File p12;

    @Before
    public void setup() {
        r.jenkins.setCrumbIssuer(null);
    }

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

    String getLogAsStringPlaintext(WorkflowRun f) throws java.io.IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        f.getLogText().writeLogTo(0, baos);
        return baos.toString();
    }

    /////////////////////////////////////////////////////////////////
    // Certificate credentials tests
    /////////////////////////////////////////////////////////////////

    // Partially from CertificateCredentialImplTest setup()
    private void prepareUploadedKeystore() throws IOException {
        prepareUploadedKeystore("myCert", "password");
    }

    private void prepareUploadedKeystore(String id, String password) throws IOException {
        if (p12 == null) {
            // Contains a private key + openvpn certs,
            // as alias named "1" (according to keytool)
            p12 = tmp.newFile("test.p12");
            FileUtils.copyURLToFile(CertificateCredentialsImplTest.class.getResource("test.p12"), p12);
        }

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

    /////////////////////////////////////////////////////////////////
    // Certificate credentials retrievability in (trusted) pipeline
    /////////////////////////////////////////////////////////////////

    String cpsScriptCertCredentialTestScriptedPipeline(String runnerTag) {
        return cpsScriptCertCredentialTestScriptedPipeline("myCert", "password", "1", runnerTag);
    }

    String cpsScriptCertCredentialTestScriptedPipeline(String id, String password, String alias, String runnerTag) {
        return  "def authentication='" + id + "';\n" +
                "def password='" + password + "';\n" +
                "def alias='" + alias + "';\n" +
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
                "String keyValue = keyStore.getKey(alias, password.toCharArray()).getEncoded().encodeBase64().toString()\n" +
                "echo  \"-----BEGIN PRIVATE KEY-----\"\n" +
                "echo keyValue\n" +
                "echo \"-----END PRIVATE KEY-----\"\n" +
                "\n" +
                "echo \"CRED-SNAP ON " + runnerTag + ":\"\n" +
                "echo credentialSnap.toString()\n" +
                "KeyStore keyStoreSnap = credentialSnap.getKeyStore();\n" +
                "KeyStoreSource kssSnap = ((CertificateCredentialsImpl) credentialSnap).getKeyStoreSource();\n" +
                "echo \"KSS-SNAP: \" + kssSnap.toString()\n" +
                "byte[] kssbSnap = kssSnap.getKeyStoreBytes();\n" +
                "echo \"KSS-SNAP bytes (len): \" + kssbSnap.length\n" +
                "String keyValueSnap = keyStoreSnap.getKey(alias, password.toCharArray()).getEncoded().encodeBase64().toString()\n" +
                "echo  \"-----BEGIN PRIVATE KEY-----\"\n" +
                "echo keyValueSnap\n" +
                "echo \"-----END PRIVATE KEY-----\"\n" +
                "\n";
    }

    @Test
    @Issue("JENKINS-70101")
    public void testCertKeyStoreReadableOnController() throws Exception {
        // Check that credentials are usable with pipeline script
        // running without a node{}
        prepareUploadedKeystore();

        // Configure the build to use the credential
        WorkflowJob proj = r.jenkins.createProject(WorkflowJob.class, "proj");
        String script = cpsScriptCredentialTestImports() +
                cpsScriptCertCredentialTestScriptedPipeline("CONTROLLER BUILT-IN");
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        r.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        r.assertLogContains("KSS-SNAP bytes", run);
    }

    @Test
    @Issue("JENKINS-70101")
    public void testCertKeyStoreReadableOnNodeLocal() throws Exception {
        // Check that credentials are usable with pipeline script
        // running on a node{} (provided by the controller)
        prepareUploadedKeystore();

        // Configure the build to use the credential
        WorkflowJob proj = r.jenkins.createProject(WorkflowJob.class, "proj");
        String script = cpsScriptCredentialTestImports() +
                "node {\n" +
                cpsScriptCertCredentialTestScriptedPipeline("CONTROLLER NODE") +
                "}\n";
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        r.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        r.assertLogContains("KSS-SNAP bytes", run);
    }

    @Test
    @Issue("JENKINS-70101")
    public void testCertKeyStoreReadableOnNodeRemote() throws Exception {
        // Check that credentials are usable with pipeline script
        // running on a remote node{} with separate JVM (check
        // that remoting/snapshot work properly)
        assumeThat("This test needs a separate build agent", this.setupAgent(), is(true));

        prepareUploadedKeystore();

        // Configure the build to use the credential
        WorkflowJob proj = r.jenkins.createProject(WorkflowJob.class, "proj");
        String script = cpsScriptCredentialTestImports() +
                "node(\"worker\") {\n" +
                cpsScriptCertCredentialTestScriptedPipeline("REMOTE NODE") +
                "}\n";
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        r.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        r.assertLogContains("KSS-SNAP bytes", run);
    }

    /////////////////////////////////////////////////////////////////
    // Certificate credentials retrievability by withCredentials() step
    /////////////////////////////////////////////////////////////////

    String cpsScriptCertCredentialTestGetKeyValue() {
        return  "@NonCPS\n" +
                "def getKeyValue(def keystoreName, def keystoreFormat, def keyPassword, def alias) {\n" +
                "    def p12file = new FileInputStream(keystoreName)\n" +
                "    def keystore = KeyStore.getInstance(keystoreFormat)\n" +
                "    keystore.load(p12file, keyPassword.toCharArray())\n" +
                "    p12file.close()\n" +
                "    def key = keystore.getKey(alias, keyPassword.toCharArray())\n" +
                "    return key.getEncoded().encodeBase64().toString()\n" +
                "}\n" +
                "\n";
    }

    String cpsScriptCertCredentialTestWithCredentials(String runnerTag) {
        return cpsScriptCertCredentialTestWithCredentials("myCert", "password", "1", runnerTag);
    }

    String cpsScriptCertCredentialTestWithCredentials(String id, String password, String alias, String runnerTag) {
        // Note: does not pass a(ny) useful (env?.)myKeyAlias to closure
        // https://issues.jenkins.io/browse/JENKINS-59331
        // https://github.com/jenkinsci/credentials-binding-plugin/blob/fcd22059ac48b87d0924ef17d5b351a3b7a89a97/src/main/java/org/jenkinsci/plugins/credentialsbinding/impl/CertificateMultiBinding.java#L80-L81
        return  "def authentication='" + id + "';\n" +
                "def password='" + password + "';\n" +
                "def alias='" + alias + "';\n" +
                "echo \"WITH-CREDENTIALS ON " + runnerTag + ":\"\n" +
                "withCredentials([certificate(\n" +
                "        credentialsId: authentication,\n" +
                "        keystoreVariable: 'keystoreName',\n" +
                "        passwordVariable: 'keyPassword',\n" +
                "        aliasVariable: 'myKeyAlias')\n" +
                "]) {\n" +
                "    echo \"Keystore bytes (len): \" + (new File(keystoreName)).length()\n" +
                "    echo \"Got expected key pass? ${keyPassword == password}\"\n" +
                "    def keystoreFormat = \"PKCS12\"\n" +
                "    def keyValue = getKeyValue(keystoreName, keystoreFormat, keyPassword, (env?.myKeyAlias ? env?.myKeyAlias : alias))\n" +
                "    println \"-----BEGIN PRIVATE KEY-----\"\n" +
                "    println keyValue\n" +
                "    println \"-----END PRIVATE KEY-----\"\n" +
                "}\n" +
                "\n";
    }

    @Test
    @Ignore("Work with keystore file requires a node")
    @Issue("JENKINS-70101")
    public void testCertWithCredentialsOnController() throws Exception {
        // Check that credentials are usable with pipeline script
        // running without a node{}
        prepareUploadedKeystore();

        // Configure the build to use the credential
        WorkflowJob proj = r.jenkins.createProject(WorkflowJob.class, "proj");
        String script = cpsScriptCredentialTestImports() +
                cpsScriptCertCredentialTestGetKeyValue() +
                cpsScriptCertCredentialTestWithCredentials("CONTROLLER BUILT-IN");
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        r.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        r.assertLogContains("END PRIVATE KEY", run);
    }

    @Test
    @Issue("JENKINS-70101")
    public void testCertWithCredentialsOnNodeLocal() throws Exception {
        // Check that credentials are usable with pipeline script
        // running on a node{} (provided by the controller)
        prepareUploadedKeystore();

        // Configure the build to use the credential
        WorkflowJob proj = r.jenkins.createProject(WorkflowJob.class, "proj");
        String script = cpsScriptCredentialTestImports() +
                cpsScriptCertCredentialTestGetKeyValue() +
                "node {\n" +
                cpsScriptCertCredentialTestWithCredentials("CONTROLLER NODE") +
                "}\n";
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        r.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        r.assertLogContains("END PRIVATE KEY", run);
    }

    @Test
    @Issue("JENKINS-70101")
    public void testCertWithCredentialsOnNodeRemote() throws Exception {
        // Check that credentials are usable with pipeline script
        // running on a remote node{} with separate JVM (check
        // that remoting/snapshot work properly)
        assumeThat("This test needs a separate build agent", this.setupAgent(), is(true));

        prepareUploadedKeystore();

        // Configure the build to use the credential
        WorkflowJob proj = r.jenkins.createProject(WorkflowJob.class, "proj");
        String script = cpsScriptCredentialTestImports() +
                cpsScriptCertCredentialTestGetKeyValue() +
                "node(\"worker\") {\n" +
                cpsScriptCertCredentialTestWithCredentials("REMOTE NODE") +
                "}\n";
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        r.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        r.assertLogContains("END PRIVATE KEY", run);
    }

    /////////////////////////////////////////////////////////////////
    // Certificate credentials retrievability by http-request-plugin
    /////////////////////////////////////////////////////////////////

    String cpsScriptCertCredentialTestHttpRequest(String runnerTag) {
        return cpsScriptCredentialTestHttpRequest("myCert", runnerTag, true);
    }

    String cpsScriptCredentialTestHttpRequest(String id, String runnerTag, Boolean withLocalCertLookup) {
        // Note: we accept any outcome (for the plugin, unresolved host is HTTP-404)
        // but it may not crash making use of the credential
        // Note: cases withLocalCertLookup also need cpsScriptCredentialTestImports()
        return  "def authentication='" + id + "';\n"
                + "\n"
                + "def msg\n"
                + (withLocalCertLookup ? (
                  "if (true) { // scoping\n"
                + "  msg = \"Finding credential...\"\n"
                + "  echo msg;" + (verbosePipelines ? " System.out.println(msg); System.err.println(msg)" : "" ) + ";\n"
                + "  StandardCredentials credential = CredentialsMatchers.firstOrNull(\n"
                + "    CredentialsProvider.lookupCredentials(\n"
                + "        StandardCredentials.class,\n"
                + "        Jenkins.instance, null, null),\n"
                + "    CredentialsMatchers.withId(authentication));\n"
                + "  msg = \"Getting keystore...\"\n"
                + "  echo msg;" + (verbosePipelines ? " System.out.println(msg); System.err.println(msg)" : "" ) + ";\n"
                + "  KeyStore keyStore = credential.getKeyStore();\n"
                + "  msg = \"Getting keystore source...\"\n"
                + "  echo msg;" + (verbosePipelines ? " System.out.println(msg); System.err.println(msg)" : "" ) + ";\n"
                + "  KeyStoreSource kss = ((CertificateCredentialsImpl) credential).getKeyStoreSource();\n"
                + "  msg = \"Getting keystore source bytes...\"\n"
                + "  echo msg;" + (verbosePipelines ? " System.out.println(msg); System.err.println(msg)" : "" ) + ";\n"
                + "  byte[] kssb = kss.getKeyStoreBytes();\n"
                + "}\n" )
                  : "" )
                + "\n"
                + "msg = \"Querying HTTPS with cert...\"\n"
                + "echo msg;" + (verbosePipelines ? " System.out.println(msg); System.err.println(msg)" : "" ) + ";\n"
                + "def response = httpRequest(url: 'https://github.xcom/api/v3',\n"
                + "                 httpMode: 'GET',\n"
                + "                 authentication: authentication,\n"
                + "                 consoleLogResponseBody: true,\n"
                + "                 contentType : 'APPLICATION_FORM',\n"
                + "                 validResponseCodes: '100:599',\n"
                + "                 quiet: false)\n"
                + "println('HTTP Request Plugin Status: '+ response.getStatus())\n"
                + "println('HTTP Request Plugin Response: '+ response.getContent())\n"
                + "\n";
    }

    @Test
    @Issue("JENKINS-70101")
    public void testCertHttpRequestOnController() throws Exception {
        // Check that credentials are usable with pipeline script
        // running without a node{}
        prepareUploadedKeystore();

        // Configure the build to use the credential
        WorkflowJob proj = r.jenkins.createProject(WorkflowJob.class, "proj");
        String script = cpsScriptCredentialTestImports() +
                cpsScriptCertCredentialTestHttpRequest("CONTROLLER BUILT-IN");
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        r.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        r.assertLogContains("HTTP Request Plugin Response: ", run);
    }

    @Test
    @Issue("JENKINS-70101")
    public void testCertHttpRequestOnNodeLocal() throws Exception {
        // Check that credentials are usable with pipeline script
        // running on a node{} (provided by the controller)
        prepareUploadedKeystore();

        // Configure the build to use the credential
        WorkflowJob proj = r.jenkins.createProject(WorkflowJob.class, "proj");
        String script = cpsScriptCredentialTestImports() +
                "node {\n" +
                cpsScriptCertCredentialTestHttpRequest("CONTROLLER NODE") +
                "}\n";
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        r.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        r.assertLogContains("HTTP Request Plugin Response: ", run);
    }

    @Test
    @Issue("JENKINS-70101")
    public void testCertHttpRequestOnNodeRemote() throws Exception {
        // Check that credentials are usable with pipeline script
        // running on a remote node{} with separate JVM (check
        // that remoting/snapshot work properly)
        assumeThat("This test needs a separate build agent", this.setupAgent(), is(true));

        prepareUploadedKeystore();

        // Configure the build to use the credential
        WorkflowJob proj = r.jenkins.createProject(WorkflowJob.class, "proj");
        String script = cpsScriptCredentialTestImports() +
                "node(\"worker\") {\n" +
                cpsScriptCertCredentialTestHttpRequest("REMOTE NODE") +
                "}\n";
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        r.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        r.assertLogContains("HTTP Request Plugin Response: ", run);
    }

    /////////////////////////////////////////////////////////////////
    // User/pass credentials tests
    /////////////////////////////////////////////////////////////////

    // Partially from UsernamePasswordCredentialsImplTest setup()
    private void prepareUsernamePassword() throws IOException {
        UsernamePasswordCredentialsImpl credentials =
                new UsernamePasswordCredentialsImpl(null,
                        "abc123", "Bob’s laptop",
                        "bob", "s3cr3t");
        SystemCredentialsProvider.getInstance().getCredentials().add(credentials);
        SystemCredentialsProvider.getInstance().save();
    }

    String cpsScriptUsernamePasswordCredentialTestHttpRequest(String runnerTag) {
        return cpsScriptCredentialTestHttpRequest("abc123", runnerTag, false);
    }

    @Test
    @Issue("JENKINS-70101")
    public void testUsernamePasswordHttpRequestOnController() throws Exception {
        // Check that credentials are usable with pipeline script
        // running without a node{}
        prepareUsernamePassword();

        // Configure the build to use the credential
        WorkflowJob proj = r.jenkins.createProject(WorkflowJob.class, "proj");
        String script = cpsScriptUsernamePasswordCredentialTestHttpRequest("CONTROLLER BUILT-IN");
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        r.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        r.assertLogContains("HTTP Request Plugin Response: ", run);
    }

    @Test
    @Issue("JENKINS-70101")
    public void testUsernamePasswordHttpRequestOnNodeLocal() throws Exception {
        // Check that credentials are usable with pipeline script
        // running on a node{} (provided by the controller)
        prepareUsernamePassword();

        // Configure the build to use the credential
        WorkflowJob proj = r.jenkins.createProject(WorkflowJob.class, "proj");
        String script =
                "node {\n" +
                cpsScriptUsernamePasswordCredentialTestHttpRequest("CONTROLLER NODE") +
                "}\n";
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        r.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        r.assertLogContains("HTTP Request Plugin Response: ", run);
    }

    @Test
    @Issue("JENKINS-70101")
    public void testUsernamePasswordHttpRequestOnNodeRemote() throws Exception {
        // Check that credentials are usable with pipeline script
        // running on a remote node{} with separate JVM (check
        // that remoting/snapshot work properly)
        assumeThat("This test needs a separate build agent", this.setupAgent(), is(true));

        prepareUsernamePassword();

        // Configure the build to use the credential
        WorkflowJob proj = r.jenkins.createProject(WorkflowJob.class, "proj");
        String script =
                "node(\"worker\") {\n" +
                cpsScriptUsernamePasswordCredentialTestHttpRequest("REMOTE NODE") +
                "}\n";
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        r.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        r.assertLogContains("HTTP Request Plugin Response: ", run);
    }

}
