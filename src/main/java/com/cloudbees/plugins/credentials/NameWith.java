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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Often there is a requirement to get the names of different credentials in order to allow the user to select from
 * multiple equivalent credentials. With Java 8 defender methods we could add a default method to {@link Credentials}
 * however given the Java requirements of Jenkins we do not have this luxury. In any case different types of credentials
 * will have different types of naming schemes, eg certificates vs username/password.
 * <p>
 * This annotation is applied to implementations or to marker interfaces. Where an implementation class is annotated,
 * that annotation will always win, even if inherited. In the absence of the base class being annotated all the
 * interfaces that the credential implements will be checked for the annotation. When checking multiple interfaces,
 * the highest priority wins. The behaviour is indeterminate if there are multiple annotated interfaces with the same
 * priority.
 *
 * @since 1.7
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface NameWith {
    /**
     * The naming class to use.
     *
     * @return The naming class to use.
     */
    @NonNull Class<? extends CredentialsNameProvider<? extends Credentials>> value();

    /**
     * When forced to name via interfaces, the highest priority among all interfaces wins.
     *
     * @return the priority among interface based providers.
     */
    int priority() default 0;
}
