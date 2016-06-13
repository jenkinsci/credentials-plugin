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

import com.cloudbees.plugins.credentials.CredentialsSelectHelper;
import hudson.Extension;
import java.util.Map;
import org.apache.commons.lang.StringUtils;

/**
 * Lists the {@link CredentialsSelectHelper.ContextResolver} instances and the context objects they resolve.
 *
 * @since 2.1.1
 */
@Extension
public class ListCredentialsContextResolversCommand extends BaseCredentialsCLICommand {
    /**
     * {@inheritDoc}
     */
    @Override
    public String getShortDescription() {
        return Messages.ListCredentialsContextResolversCommand_ShortDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int run() throws Exception {
        Map<String, CredentialsSelectHelper.ContextResolver> resolversByName =
                CredentialsSelectHelper.getResolversByName();
        int maxNameLen = 0, maxDisplayLen = 0;
        for (Map.Entry<String, CredentialsSelectHelper.ContextResolver> entry : resolversByName.entrySet()) {
            maxNameLen = Math.max(maxNameLen, entry.getKey().length());
            maxDisplayLen = Math.max(maxDisplayLen,
                    CredentialsSelectHelper.ContextResolver.displayName(entry.getValue()).length());
        }
        stdout.println(StringUtils.rightPad("Name", maxNameLen) + " Resolves");
        stdout.println(StringUtils.repeat("=", maxNameLen) + " " + StringUtils.repeat("=", maxDisplayLen));
        for (Map.Entry<String, CredentialsSelectHelper.ContextResolver> entry : resolversByName.entrySet()) {
            stdout.println(StringUtils.rightPad(entry.getKey(), maxNameLen)
                    + " "
                    + CredentialsSelectHelper.ContextResolver.displayName(entry.getValue()));
        }
        return 0;
    }
}
