/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package com.cloudbees.plugins.credentials.impl;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import java.util.logging.Level;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

public class UsernamePasswordCredentialsImplTest {
    
    @Rule public JenkinsRule r = new JenkinsRule(); // needed for Secret.fromString to work
    @Rule public LoggerRule logging = new LoggerRule().record(CredentialsNameProvider.class, Level.FINE);
    
    @Test public void displayName() {
        UsernamePasswordCredentialsImpl creds = new UsernamePasswordCredentialsImpl(null, "abc123", "Bob’s laptop", "bob", "s3cr3t");
        assertEquals("bob/****** (Bob’s laptop)", CredentialsNameProvider.name(creds));
        creds.setUsernameSecret(true);
        assertEquals("Bob’s laptop", CredentialsNameProvider.name(creds));
        creds = new UsernamePasswordCredentialsImpl(null, "abc123", null, "bob", "s3cr3t");
        assertEquals("bob/******", CredentialsNameProvider.name(creds));
        creds.setUsernameSecret(true);
        assertEquals("abc123", CredentialsNameProvider.name(creds));
    }

}
