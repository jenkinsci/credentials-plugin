/*
 * The MIT License
 *
 * Copyright (c) 2011-2013, CloudBees, Inc., Stephen Connolly.
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
import org.jvnet.tiger_types.Types;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Translates {@link Credentials} from one type into another. We only support point-to-point translation, not a chain.
 * Translation is designed to help plugins migrate towards common credential interfaces once they have been
 * standardized, so the intention is that a plugin that implemented an old credential type and has migrated to the
 * new type would also provide a translator.
 *
 * @see ResolveWith
 * @since 1.6
 */
public abstract class CredentialsResolver<F extends Credentials, T extends Credentials> {

    /**
     * Our logger.
     *
     * @since 1.6
     */
    private static final Logger LOGGER = Logger.getLogger(CredentialsResolver.class.getName());

    /**
     * The class we can resolve from.
     */
    private final Class<F> fromClass;

    /**
     * The class we can resolve to.
     */
    private final Class<T> toClass;

    /**
     * Constructor.
     *
     * @param fromClass the class to resolve from.
     * @param toClass   the class to resolve to.
     */
    protected CredentialsResolver(Class<F> fromClass, Class<T> toClass) {
        this.fromClass = fromClass;
        this.toClass = toClass;
    }

    /**
     * Infers the to type of the corresponding {@link Credentials} from the outer class.
     * This version works when you follow the common convention, where a resolver
     * is written as the static nested class of the resolved class.
     *
     * @param fromClass the class to resolve from.
     */
    protected CredentialsResolver(Class<F> fromClass) {
        this.fromClass = fromClass;
        this.toClass = (Class<T>) getClass().getEnclosingClass();
        if (toClass == null) {
            throw new AssertionError(getClass()
                    + " doesn't have an outer class. Use the constructor that takes the Class object explicitly.");
        }

        // detect an type error
        Type bt = Types.getBaseClass(getClass(), Credentials.class);
        if (bt instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) bt;
            // this 't' is the closest approximation of T of Descriptor<T>.
            Class t = Types.erasure(pt.getActualTypeArguments()[0]);
            if (!t.isAssignableFrom(fromClass)) {
                throw new AssertionError("Outer class " + fromClass + " of " + getClass() + " is not assignable to " + t
                        + ". Perhaps wrong outer class?");
            }
        }
    }

    /**
     * Returns the class to resolve from.
     *
     * @return the class to resolve from.
     */
    public Class<F> getFromClass() {
        return fromClass;
    }

    /**
     * Returns the class to resolve to.
     *
     * @return the class to resolve to.
     */
    public Class<T> getToClass() {
        return toClass;
    }

    /**
     * Resolves the supplied credentials.
     *
     * @param original the original type of credential.
     * @return the resolved credentials or the original if they already implement the required interface.
     */
    public T resolve(F original) {
        if (toClass.isInstance(original)) {
            return toClass.cast(original);
        }
        // if proven hot-spot, may want to consider adding a cache... but we shouldn't be looking up
        // credentials all the time, only when providing user with a list of credentials or when looking up
        // one for use.
        return doResolve(original);
    }

    /**
     * Resolves the supplied credentials.
     *
     * @param originals credentials of the original type.
     * @return the resolved credentials.
     */
    public final List<T> resolve(Collection<? extends F> originals) {
        List<T> result = new ArrayList<T>();
        for (F original : originals) {
            result.add(resolve(original));
        }
        return result;
    }

    /**
     * Resolves the supplied credentials.
     *
     * @param original the original type of credential.
     * @return the resolved credentials.
     */
    protected abstract T doResolve(F original);

    /**
     * Retrieves the {@link CredentialsResolver} for the specified type (if it exists)
     *
     * @param clazz the type.
     * @param <C>   the type.
     * @return the {@link CredentialsResolver} to use or {@code null} if no resolver is required.
     */
    @CheckForNull
    @SuppressWarnings("unchecked")
    public static <C extends Credentials> CredentialsResolver<Credentials, C> getResolver(@NonNull Class<C> clazz) {
        final ResolveWith resolveWith = clazz.getAnnotation(ResolveWith.class);
        if (resolveWith != null) {
            // if the reflective instantiation proves a hot point, put a cache in front.
            CredentialsResolver<Credentials, C> resolver;
            try {
                resolver = resolveWith.value().newInstance();
            } catch (InstantiationException e) {
                LOGGER.log(Level.WARNING, "Could not instantiate resolver: " + resolveWith.value(), e);
                return null;
            } catch (IllegalAccessException e) {
                LOGGER.log(Level.WARNING, "Could not instantiate resolver: " + resolveWith.value(), e);
                return null;
            }
            if (clazz.isAssignableFrom(resolver.getToClass())) {
                return resolver;
            }
            LOGGER.log(Level.SEVERE, "Resolver {0} for type {1} resolves to {2} which is not assignable to {1}",
                    new Object[]{resolver.getClass(), clazz, resolver.getToClass()});
        }
        return null;
    }
}
