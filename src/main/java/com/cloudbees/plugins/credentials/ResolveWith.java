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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * As credentials evolve we need to be able to map legacy credential types to newer common interfaces and
 * implementations.
 * For example code that requires a credential that holds a username and password should be looking for
 * {@link com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials}, and existing legacy
 * implementations of corresponding credential types should be using {@code readResolve()} to map down to that
 * interface.
 * But what happens to legacy code that is looking for the legacy type? By annotating the legacy type with
 * {@link ResolveWith} we can provide the legacy code with the credentials it seeks while migrating those legacy
 * types to the common parent. For example
 * <pre>
 *     public class SSHUserPassCredential {
 *         // ...
 *         {@literal @Extension}
 *         public static class DescriptorImpl extends CredentialsDescriptor {
 *             // ...
 *         }
 *     }
 * </pre>
 * should be transformed into
 * <pre>
 *     {@literal @ResolveWith(SSHUserPassCredentials.ResolverImpl.class)}
 *     public class SSHUserPassCredential implements StandardUsernamePasswordCredentials {
 *         // ...
 *         public static class ResolverImpl extends CredentialsResolver {
 *             // ...
 *         }
 *     }
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ResolveWith {
    /**
     * The {@link CredentialsResolver} to use for the annotated class.
     *
     * @return the {@link CredentialsResolver} to use for the annotated class.
     */
    Class<? extends CredentialsResolver> value();
}
