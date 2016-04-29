/*
 * The MIT License
 *
 * Copyright (c) 2011-2012, CloudBees, Inc., Stephen Connolly.
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import java.io.Serializable;

/**
 * A generic type of credentials. In general please extend from {@link BaseCredentials} rather than implement this
 * interface. The interface is provided:<ol>
 * <li>To allow for orthogonal traits to be built up in interfaces that extend credentials,
 * e.g. username/password vs username/ssh-key vs email/password - a single credentials impl could provide all four
 * fields and different credentials consumers might request the different flavours</li>
 * <li>In order to facility cases where it is just not possible to change the
 * base class when retrofitting support for credentials into an existing plugin.</li>
 * </ol>
 */
public interface Credentials extends Describable<Credentials>, Serializable, ExtensionPoint {
    /**
     * Gets the scope of the credential.
     *
     * @return the scope of the credential.
     */
    @CheckForNull
    CredentialsScope getScope();

    /**
     * {@inheritDoc}
     */
    @NonNull
    @SuppressWarnings("unchecked")
    CredentialsDescriptor getDescriptor();
}
