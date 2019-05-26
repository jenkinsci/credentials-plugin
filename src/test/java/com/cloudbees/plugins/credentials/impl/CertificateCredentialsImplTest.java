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
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.FilePath;
import hudson.cli.CLICommandInvoker;
import hudson.cli.UpdateJobCommand;
import hudson.model.Job;
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
import java.nio.file.Files;
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

    @Before
    public void setup() throws IOException {
        p12 = tmp.newFile("test.p12");
        FileUtils.copyURLToFile(CertificateCredentialsImplTest.class.getResource("test.p12"), p12);
    }

    @Test
    public void displayName() throws IOException {
        SecretBytes uploadedKeystore = SecretBytes.fromBytes(Files.readAllBytes(p12.toPath()));
        CertificateCredentialsImpl.UploadedKeyStoreSource storeSource = new CertificateCredentialsImpl.UploadedKeyStoreSource(uploadedKeystore);
        assertEquals("EMAILADDRESS=me@myhost.mydomain, CN=pkcs12, O=Fort-Funston, L=SanFrancisco, ST=CA, C=US", CredentialsNameProvider.name(new CertificateCredentialsImpl(null, "abc123", null, "password", storeSource)));
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
        Domain domain = new Domain("test", "test", Collections.EMPTY_LIST);
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

        Domain domain = new Domain("test", "test", Collections.EMPTY_LIST);

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
