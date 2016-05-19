/*
 * The MIT License
 *
 * Copyright (c) 2014-2016, CloudBees, Inc..
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
import java.util.Arrays;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconType;

/**
 * Jenkins Plugin impl.
 */
public class PluginImpl extends Plugin {

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() throws Exception {
        super.start();

        for (String name: new String[]{
                "credential",
                "credentials",
                "domain",
                "new-credential",
                "new-domain",
                "move",
                "certificate",
                "userpass",
                "system-store",
                "user-store",
                "folder-store",
        }) {
            IconSet.icons.addIcon(new Icon(
                    String.format("icon-credentials-%s icon-sm", name),
                    String.format("credentials/images/16x16/%s.png", name),
                            Icon.ICON_SMALL_STYLE, IconType.PLUGIN)
            );
            IconSet.icons.addIcon(new Icon(
                    String.format("icon-credentials-%s icon-md", name),
                    String.format("credentials/images/24x24/%s.png", name),
                            Icon.ICON_MEDIUM_STYLE, IconType.PLUGIN)
            );
            IconSet.icons.addIcon(new Icon(
                    String.format("icon-credentials-%s icon-lg", name),
                    String.format("credentials/images/32x32/%s.png", name),
                            Icon.ICON_LARGE_STYLE, IconType.PLUGIN)
            );
            IconSet.icons.addIcon(new Icon(
                    String.format("icon-credentials-%s icon-xlg", name),
                    String.format("credentials/images/48x48/%s.png", name),
                            Icon.ICON_XLARGE_STYLE, IconType.PLUGIN)
            );
        }
    }
}
