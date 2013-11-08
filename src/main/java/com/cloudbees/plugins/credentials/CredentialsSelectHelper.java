/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc., Stephen Connolly.
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

import com.cloudbees.plugins.credentials.domains.Domain;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.security.Permission;
import hudson.util.FormApply;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * A descriptor used to assist the c:select tag with allowing in-place addition of credentials.
 *
 * @author Stephen Connolly
 */
@Extension
public class CredentialsSelectHelper extends Descriptor<CredentialsSelectHelper> implements
        Describable<CredentialsSelectHelper> {

    public static final Permission CREATE = CredentialsProvider.CREATE;

    /**
     * {@inheritDoc}
     */
    public CredentialsSelectHelper() {
        super(CredentialsSelectHelper.class);
    }

    /**
     * {@inheritDoc}
     */
    public CredentialsSelectHelper getDescriptor() {
        return this;
    }

    public DescriptorExtensionList<Credentials, Descriptor<Credentials>> getCredentialsDescriptors() {
        return Jenkins.getInstance().getDescriptorList(Credentials.class);
    }

    public CredentialsStoreAction.DomainWrapper getWrapper() {
        return new CredentialsStoreAction() {
            @NonNull
            @Override
            public CredentialsStore getStore() {
                return CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
            }
        }.getDomain("_");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return Messages.CredentialsSelectHelper_DisplayName();
    }

    @RequirePOST
    public void doAddCredentials(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        final CredentialsStoreAction.DomainWrapper wrapper = getWrapper();
        if (!wrapper.getStore().isDomainsModifiable()) {
            hudson.util.HttpResponses.status(400).generateResponse(req, rsp, null);
            FormApply.applyResponse("window.alert('Domain is read-only')").generateResponse(req, rsp, null);
        }
        wrapper.getStore().checkPermission(CredentialsStoreAction.CREATE);
        JSONObject data = req.getSubmittedForm();
        Credentials credentials = req.bindJSON(Credentials.class, data.getJSONObject("credentials"));
        wrapper.getStore().addCredentials(wrapper.getDomain(), credentials);
        FormApply.applyResponse("window.credentials.refreshAll();").generateResponse(req, rsp, null);
    }
}
