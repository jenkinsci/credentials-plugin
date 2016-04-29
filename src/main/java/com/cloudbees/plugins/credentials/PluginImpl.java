/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.Plugin;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconType;

/**
 * Jenkins Plugin impl.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class PluginImpl extends Plugin {

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() throws Exception {
        super.start();

        // Register the small (16x16) icons...
        IconSet.icons.addIcon(new Icon("icon-credentials-credential icon-sm", "credentials/images/16x16/credential.png",
                Icon.ICON_SMALL_STYLE, IconType.PLUGIN));
        IconSet.icons.addIcon(
                new Icon("icon-credentials-credentials icon-sm", "credentials/images/16x16/credentials.png",
                        Icon.ICON_SMALL_STYLE, IconType.PLUGIN));
        IconSet.icons.addIcon(new Icon("icon-credentials-domain icon-sm", "credentials/images/16x16/domain.png",
                Icon.ICON_SMALL_STYLE, IconType.PLUGIN));
        IconSet.icons.addIcon(
                new Icon("icon-credentials-new-credential icon-sm", "credentials/images/16x16/new-credential.png",
                        Icon.ICON_SMALL_STYLE, IconType.PLUGIN));
        IconSet.icons.addIcon(new Icon("icon-credentials-new-domain icon-sm", "credentials/images/16x16/new-domain.png",
                Icon.ICON_SMALL_STYLE, IconType.PLUGIN));

        // Register the medium (24x24) icons...
        IconSet.icons.addIcon(new Icon("icon-credentials-credential icon-md", "credentials/images/24x24/credential.png",
                Icon.ICON_MEDIUM_STYLE, IconType.PLUGIN));
        IconSet.icons.addIcon(
                new Icon("icon-credentials-credentials icon-md", "credentials/images/24x24/credentials.png",
                        Icon.ICON_MEDIUM_STYLE, IconType.PLUGIN));
        IconSet.icons.addIcon(new Icon("icon-credentials-domain icon-md", "credentials/images/24x24/domain.png",
                Icon.ICON_MEDIUM_STYLE, IconType.PLUGIN));
        IconSet.icons.addIcon(
                new Icon("icon-credentials-new-credential icon-md", "credentials/images/24x24/new-credential.png",
                        Icon.ICON_MEDIUM_STYLE, IconType.PLUGIN));
        IconSet.icons.addIcon(new Icon("icon-credentials-new-domain icon-md", "credentials/images/24x24/new-domain.png",
                Icon.ICON_MEDIUM_STYLE, IconType.PLUGIN));

        // Register the large (32x32) icons...
        IconSet.icons.addIcon(new Icon("icon-credentials-credential icon-lg", "credentials/images/32x32/credential.png",
                Icon.ICON_LARGE_STYLE, IconType.PLUGIN));
        IconSet.icons.addIcon(
                new Icon("icon-credentials-credentials icon-lg", "credentials/images/32x32/credentials.png",
                        Icon.ICON_LARGE_STYLE, IconType.PLUGIN));
        IconSet.icons.addIcon(new Icon("icon-credentials-domain icon-lg", "credentials/images/32x32/domain.png",
                Icon.ICON_LARGE_STYLE, IconType.PLUGIN));
        IconSet.icons.addIcon(
                new Icon("icon-credentials-new-credential icon-lg", "credentials/images/32x32/new-credential.png",
                        Icon.ICON_LARGE_STYLE, IconType.PLUGIN));
        IconSet.icons.addIcon(new Icon("icon-credentials-new-domain icon-lg", "credentials/images/32x32/new-domain.png",
                Icon.ICON_LARGE_STYLE, IconType.PLUGIN));

        // Register the x-large (48x48) icons...
        IconSet.icons.addIcon(
                new Icon("icon-credentials-credential icon-xlg", "credentials/images/48x48/credential.png",
                        Icon.ICON_XLARGE_STYLE, IconType.PLUGIN));
        IconSet.icons.addIcon(
                new Icon("icon-credentials-credentials icon-xlg", "credentials/images/48x48/credentials.png",
                        Icon.ICON_XLARGE_STYLE, IconType.PLUGIN));
        IconSet.icons.addIcon(new Icon("icon-credentials-domain icon-xlg", "credentials/images/48x48/domain.png",
                Icon.ICON_XLARGE_STYLE, IconType.PLUGIN));
        IconSet.icons.addIcon(
                new Icon("icon-credentials-new-credential icon-xlg", "credentials/images/48x48/new-credential.png",
                        Icon.ICON_XLARGE_STYLE, IconType.PLUGIN));
        IconSet.icons.addIcon(
                new Icon("icon-credentials-new-domain icon-xlg", "credentials/images/48x48/new-domain.png",
                        Icon.ICON_XLARGE_STYLE, IconType.PLUGIN));
    }
}
