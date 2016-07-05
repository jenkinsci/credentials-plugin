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
package com.cloudbees.plugins.credentials;

/**
 * This exception can be thrown by any get method in a {@link Credentials} subclass to indicate that the requested
 * property is unavailable.
 *
 * @since 2.1.5
 */
public class CredentialsUnavailableException extends RuntimeException {

    private final String property;

    /**
     * {@inheritDoc}
     */
    public CredentialsUnavailableException(String property) {
        this.property = property;
    }

    /**
     * {@inheritDoc}
     */
    public CredentialsUnavailableException(String property, String message) {
        super(message);
        this.property = property;
    }

    /**
     * {@inheritDoc}
     */
    public CredentialsUnavailableException(String property, String message, Throwable cause) {
        super(message, cause);
        this.property = property;
    }

    /**
     * {@inheritDoc}
     */
    public CredentialsUnavailableException(String property, Throwable cause) {
        super(cause);
        this.property = property;
    }

    /**
     * Reports the property name.
     *
     * @return the property name.
     */
    public String getProperty() {
        return property;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMessage() {
        String message = super.getMessage();
        return message == null ? String.format("Property '%s' is currently unavailable", property)
                : String.format("Property '%s' is currently unavailable, reason: %s", property, message);
    }
}
