/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc..
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
import hudson.Util;
import hudson.model.Action;
import hudson.model.ModelObject;
import hudson.security.Permission;

import java.util.Map;
import java.util.TreeMap;

/**
 * @author Stephen Connolly
 */
public abstract class CredentialsStoreAction implements Action {

    public static final Permission VIEW = CredentialsProvider.VIEW;

    @NonNull
    public abstract CredentialsStore getStore();

    public String getIconFileName() {
        if (CredentialsProvider.allCredentialsDescriptors().isEmpty()) return null;
        return getStore().hasPermission(CredentialsProvider.VIEW)
                ? "/plugin/credentials/images/48x48/credentials.png"
                : null;
    }

    public String getDisplayName() {
        return Messages.CredentialsStoreAction_DisplayName();
    }

    public String getUrlName() {
        return "credential-store";
    }

    public Map<String,DomainWrapper> getDomains() {
        Map<String,DomainWrapper> result = new TreeMap<String, DomainWrapper>();
        for (Domain d: getStore().getDomains()) {
            String name;
            if (d == Domain.global())
                name = "_";
            else
                name = Util.rawEncode(d.getName());
            result.put(name, new DomainWrapper(d));
        }
        return result;
    }

    public DomainWrapper getDomain(String name) {
        return getDomains().get(name);
    }

    public class DomainWrapper implements ModelObject {

        private final Domain domain;

        public DomainWrapper(Domain domain) {
            this.domain = domain;
        }

        public CredentialsStore getStore() {
            return CredentialsStoreAction.this.getStore();
        }

        public String getDisplayName() {
            return domain == Domain.global() ? Messages.CredentialsStoreAction_GlobalDomainDisplayName() : domain.getName();
        }

        public String getDescription() {
            return domain == Domain.global() ? Messages.CredentialsStoreAction_GlobalDomainDescription() : domain.getDescription();
        }

    }
}
