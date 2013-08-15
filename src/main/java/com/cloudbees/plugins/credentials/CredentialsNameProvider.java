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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Provides names for credentials.
 *
 * @see NameWith
 * @since 1.7
 */
public abstract class CredentialsNameProvider<C extends Credentials> {

    /**
     * Name the credential.
     *
     * @param credentials the credential to name.
     * @return the name.
     */
    @NonNull
    public abstract String getName(@NonNull C credentials);

    /**
     * Name the credential.
     *
     * @param credentials the credential to name.
     * @return the name.
     */
    @NonNull
    public static String name(@NonNull Credentials credentials) {
        credentials.getClass(); // throw NPE if null
        @CheckForNull
        NameWith nameWith = credentials.getClass().getAnnotation(NameWith.class);
        if (!isValidNameWithAnnotation(nameWith)) {
            Queue<Class<?>> queue = new LinkedList<Class<?>>();
            queue.addAll(Arrays.asList(credentials.getClass().getInterfaces()));
            while (!queue.isEmpty()) {
                Class<?> interfaze = queue.remove();
                if (interfaze.getInterfaces().length > 0) {
                    queue.addAll(Arrays.asList(interfaze.getInterfaces()));
                }
                NameWith annotation = interfaze.getAnnotation(NameWith.class);
                if (!isValidNameWithAnnotation(annotation)) {
                    continue;
                }
                if (nameWith == null || nameWith.priority() < annotation.priority()) {
                    nameWith = annotation;
                }
            }
        }
        if (isValidNameWithAnnotation(nameWith)) {
            try {
                CredentialsNameProvider nameProvider = nameWith.value().newInstance();
                return nameProvider.getName(credentials);
            } catch (ClassCastException e) {
                // ignore
            } catch (InstantiationException e) {
                // ignore
            } catch (IllegalAccessException e) {
                // ignore
            }
        }
        try {
            return credentials.getDescriptor().getDisplayName();
        } catch (AssertionError e) {
            return credentials.getClass().getSimpleName();
        }
    }

    /**
     * Check that the annotation is valid.
     *
     * @param nameWith the annotation.
     * @return {@code true} only if the annotation is valid.
     */
    @SuppressWarnings("ConstantConditions")
    private static boolean isValidNameWithAnnotation(@CheckForNull NameWith nameWith) {
        return nameWith != null && nameWith.value() != null;
    }
}
