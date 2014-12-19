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

package com.cloudbees.plugins.credentials;

import org.junit.Test;
import static org.junit.Assert.*;

public class CredentialsNameProviderTest {

    @Test public void notAnnotated() {
        assertEquals("C0", CredentialsNameProvider.name(new C0()));
    }
    private static class C0 extends TestCredentials {}

    @Test public void directlyAnnotated() {
        assertEquals("C1N", CredentialsNameProvider.name(new C1()));
    }
    @NameWith(C1N.class)
    private static class C1 extends TestCredentials {}
    public static class C1N extends TestCredentialsNameProvider {}

    @Test public void superclassAnnotated() {
        assertEquals("C2N", CredentialsNameProvider.name(new C3()));
    }
    @NameWith(C2N.class)
    private static class C2 extends TestCredentials {}
    public static class C2N extends TestCredentialsNameProvider {}
    private static class C3 extends C2 {}

    @Test public void interfaceAnnotated() {
        assertEquals("I1N", CredentialsNameProvider.name(new C4()));
    }
    @NameWith(I1N.class)
    private interface I1 extends Credentials {}
    public static class I1N extends TestCredentialsNameProvider {}
    private static class C4 extends TestCredentials implements I1 {}

    @Test public void indirectInterfaceAnnotated() {
        assertEquals("I2N", CredentialsNameProvider.name(new C5()));
    }
    @NameWith(I2N.class)
    private interface I2 extends Credentials {}
    public static class I2N extends TestCredentialsNameProvider {}
    private interface I3 extends I2 {}
    private static class C5 extends TestCredentials implements I3 {}

    @Test public void interfaceOfSuperclassAnnotated() {
        assertEquals("I4N", CredentialsNameProvider.name(new C7()));
    }
    @NameWith(I4N.class)
    private interface I4 extends Credentials {}
    public static class I4N extends TestCredentialsNameProvider {}
    private static class C6 extends TestCredentials implements I4 {}
    private static class C7 extends C6 {}

    @Test public void overrideSuperclassAnnotation() {
        assertEquals("C9N", CredentialsNameProvider.name(new C9()));
    }
    @NameWith(C8N.class)
    private static class C8 extends TestCredentials {}
    public static class C8N extends TestCredentialsNameProvider {}
    @NameWith(C9N.class)
    private static class C9 extends C8 {}
    public static class C9N extends TestCredentialsNameProvider {}

    @Test public void overrideInterfaceAnnotation() {
        assertEquals("C10N", CredentialsNameProvider.name(new C10()));
    }
    @NameWith(I5N.class)
    private interface I5 extends Credentials {}
    public static class I5N extends TestCredentialsNameProvider {}
    @NameWith(C10N.class)
    private static class C10 extends TestCredentials implements I5 {}
    public static class C10N extends TestCredentialsNameProvider {}

    @Test public void interfacePriorities() {
        assertEquals("I7N", CredentialsNameProvider.name(new C11()));
        assertEquals("I7N", CredentialsNameProvider.name(new C12()));
    }
    @NameWith(value=I6N.class, priority=1)
    private interface I6 extends Credentials {}
    public static class I6N extends TestCredentialsNameProvider {}
    @NameWith(value=I7N.class, priority=2)
    private interface I7 extends Credentials {}
    public static class I7N extends TestCredentialsNameProvider {}
    private static class C11 extends TestCredentials implements I6, I7 {}
    private static class C12 extends TestCredentials implements I7, I6 {}

    @Test public void interfaceViaSuperclassPriorities() {
        assertEquals("I9N", CredentialsNameProvider.name(new C14()));
    }
    @NameWith(value=I8N.class, priority=1)
    private interface I8 extends Credentials {}
    public static class I8N extends TestCredentialsNameProvider {}
    private static class C13 extends TestCredentials implements I8 {}
    @NameWith(value=I9N.class, priority=2)
    private interface I9 extends Credentials {}
    public static class I9N extends TestCredentialsNameProvider {}
    private interface I10 extends I9 {}
    private static class C14 extends C13 implements I10 {}

    private static abstract class TestCredentials implements Credentials {
        @Override public CredentialsScope getScope() {
            return CredentialsScope.GLOBAL;
        }
        @Override public CredentialsDescriptor getDescriptor() {
            return new CredentialsDescriptor() {
                @Override public String getDisplayName() {
                    return TestCredentials.this.getClass().getSimpleName();
                }
            };
        }
    }

    private static abstract class TestCredentialsNameProvider extends CredentialsNameProvider<Credentials> {
         @Override public String getName(Credentials credentials) {
            return getClass().getSimpleName();
        }
    }

}