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
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import hudson.cli.CLICommand;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import hudson.util.XStream2;
import jenkins.util.xml.XMLUtils;
import org.apache.commons.lang.StringUtils;
import org.xml.sax.SAXException;

/**
 * Base class for the Credentials CLI commands.
 *
 * @since 2.1.1
 */
public abstract class BaseCredentialsCLICommand extends CLICommand {
    protected static Credentials getCredentialsById(CredentialsStore store, Domain domain, String id) {
        List<Credentials> credentialsList = store.getCredentials(domain);
        Set<String> ids = new HashSet<>(credentialsList.size());
        Credentials existing = null;
        int index = 0;
        for (Credentials c : credentialsList) {
            String cid;
            if (c instanceof IdCredentials) {
                cid = ((IdCredentials) c).getId();
            } else {
                while (ids.contains("index-" + index)) {
                    index++;
                }
                cid = "index-" + index;
                index++;
            }
            if (id.equals(cid)) {
                existing = c;
                break;
            }
            ids.add(cid);
        }
        return existing;
    }

    protected static Domain getDomainByName(CredentialsStore store, String domain) {
        if (StringUtils.equals("_", domain) || StringUtils.isBlank(domain) || "(global)".equals(domain)) {
            return Domain.global();
        } else {
            for (Domain d : store.getDomains()) {
                if (domain.equals(d.getName())) {
                    return d;
                }
            }
        }
        return null;
    }


    protected static HierarchicalStreamReader safeXmlStreamReader(InputStream stream) throws IOException {
        return safeXmlStreamReader(new StreamSource(stream));
    }

    protected static HierarchicalStreamReader safeXmlStreamReader(Source source) throws IOException {
        final StringWriter out = new StringWriter();
        try {
            XMLUtils.safeTransform(source, new StreamResult(out));
            out.close();
        } catch (TransformerException | SAXException e) {
            throw new IOException("Failed to parse", e);
        }
        return XStream2.getDefaultDriver().createReader(new StringReader(out.toString()));

    }
}
