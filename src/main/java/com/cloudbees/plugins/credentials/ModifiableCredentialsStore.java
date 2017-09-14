/*
 * The MIT License
 *
 * Copyright (c) 2013-2016, CloudBees, Inc., Stephen Connolly.
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
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.BulkChange;
import hudson.ExtensionList;
import hudson.Functions;
import hudson.Util;
import hudson.model.*;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.AccessDeniedException2;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.net.URI;
import java.util.*;

/**
 * A store of {@link Credentials}. Each {@link ModifiableCredentialsStore} is associated with one and only one
 * {@link CredentialsProvider} though a {@link CredentialsProvider} may provide multiple {@link ModifiableCredentialsStore}s
 * (for example a folder scoped {@link CredentialsProvider} may provide a {@link ModifiableCredentialsStore} for each folder
 * or a user scoped {@link CredentialsProvider} may provide a {@link ModifiableCredentialsStore} for each user).
 *
 * @author Stephen Connolly
 * @since 1.8
 */
interface ModifiableCredentialsStore extends ModifiableDomainsCredentialsStore, ModifiableItemsCredentialsStore {
}
