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

import com.cloudbees.plugins.credentials.common.IdCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;


/**
 * Provides names for credentials.
 *
 * @see NameWith
 * @since 1.7
 */
public abstract class CredentialsNameProvider<C extends Credentials> {

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(CredentialsNameProvider.class.getName());

    /**
     * Name the credential.
     *
     * @param credentials the credential to name.
     * @return the name.
     */
    @NonNull
    public static String name(@NonNull Credentials credentials) {
        try {
            Result result = name(credentials, credentials.getClass());
            if (result != null) {
                return result.name;
            }
        } catch (Exception e) {
            if (LOGGER.isLoggable(Level.FINE)) {
                if (credentials instanceof IdCredentials) {
                    LogRecord lr = new LogRecord(Level.FINE, "Credential ID {0} Type {1}: Could not infer name");
                    lr.setParameters(new Object[]{((IdCredentials) credentials).getId(), credentials.getClass()});
                    lr.setThrown(e);
                    LOGGER.log(lr);
                } else {
                    LogRecord lr = new LogRecord(Level.FINE, "Credential Type {0}: Could not infer name");
                    lr.setParameters(new Object[]{credentials.getClass()});
                    lr.setThrown(e);
                    LOGGER.log(lr);
                }
            }
        }
        try {
            return credentials.getDescriptor().getDisplayName();
        } catch (AssertionError e) {
            return credentials.getClass().getSimpleName();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // missing type token in CredentialsNameProvider to get RTTI
    private static
    @CheckForNull
    Result name(@NonNull Credentials credentials, @NonNull Class<?> clazz) {
        NameWith nameWith = clazz.getAnnotation(NameWith.class);
        if (nameWith != null) {
            try {
                CredentialsNameProvider nameProvider = nameWith.value().getDeclaredConstructor().newInstance();
                String name = nameProvider.getName(credentials);
                if (!name.isEmpty()) {
                    LOGGER.fine(() -> "named `" + name + "` from " + nameProvider);
                    return new Result(name, nameWith.priority());
                }
            } catch (ClassCastException | InstantiationException | IllegalAccessException
                     | InvocationTargetException | NoSuchMethodException e) {
                // ignore
            }
        }
        List<Class<?>> supertypes = new ArrayList<>();
        Class<?> supe = clazz.getSuperclass();
        if (supe != null) {
            supertypes.add(supe);
        }
        supertypes.addAll(Arrays.asList(clazz.getInterfaces()));
        Result result = null;
        for (Class<?> supertype : supertypes) {
            Result _result = name(credentials, supertype);
            if (_result != null && (result == null || result.priority < _result.priority)) {
                result = _result;
            }
        }
        return result;
    }

    /**
     * Name the credential.
     *
     * @param credentials the credential to name.
     * @return the name.
     */
    @NonNull
    public abstract String getName(@NonNull C credentials);

    private static final class Result {
        final String name;
        final int priority;

        Result(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }
    }

}
