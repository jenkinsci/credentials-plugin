/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc..
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
package com.cloudbees.plugins.credentials.cli;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.Extension;
import org.kohsuke.args4j.Argument;

/**
 * Deletes a credential.
 *
 * @since 2.1.1
 */
@Extension
public class DeleteCredentialsCommand extends BaseCredentialsCLICommand {
    @Argument(metaVar = "STORE", usage = "Store Id", required = true)
    public CredentialsStore store;

    @Argument(metaVar = "DOMAIN", usage = "Domain Name", required = true, index = 1)
    public String domain;

    @Argument(metaVar = "CREDENTIAL", usage = "Credential Id", required = true, index = 2)
    public String id;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getShortDescription() {
        return Messages.DeleteCredentialsCommand_ShortDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int run() throws Exception {
        store.checkPermission(CredentialsProvider.DELETE);
        Domain domain = getDomainByName(store, this.domain);
        if (domain == null) {
            stderr.println("No such domain");
            return 2;
        }
        Credentials existing = getCredentialsById(store, domain, id);
        if (existing == null) {
            stderr.println("No such credential");
            return 3;
        }

        if (store.removeCredentials(domain, existing)) {
            return 0;
        }
        stderr.println("Not deleted");
        return 1;
    }

}
