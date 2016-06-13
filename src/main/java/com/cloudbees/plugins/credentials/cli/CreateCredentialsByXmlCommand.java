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
import hudson.model.Items;
import org.kohsuke.args4j.Argument;

/**
 * Create a new credentials instance by XML.
 *
 * @since 2.1.1
 */
@Extension
public class CreateCredentialsByXmlCommand extends BaseCredentialsCLICommand {
    @Argument(metaVar = "STORE", usage = "Store Id", required = true)
    public CredentialsStore store;

    @Argument(metaVar = "DOMAIN", usage = "Domain Name", required = true, index = 1)
    public String domain;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getShortDescription() {
        return Messages.CreateCredentialsByXmlCommand_ShortDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int run() throws Exception {
        store.checkPermission(CredentialsProvider.CREATE);
        Domain domain = getDomainByName(store, this.domain);
        if (domain == null) {
            stderr.println("No such domain");
            return 2;
        }

        Credentials credentials = (Credentials) Items.XSTREAM.unmarshal(safeXmlStreamReader(stdin));
        if (store.addCredentials(domain, credentials)) {
            return 0;
        }
        stderr.println("No change");
        return 1;
    }
}
