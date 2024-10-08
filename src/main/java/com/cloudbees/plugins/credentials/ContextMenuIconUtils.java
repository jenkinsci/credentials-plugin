/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
import hudson.Functions;
import hudson.model.Action;
import java.lang.reflect.Field;
import jenkins.model.ModelObjectWithContextMenu;
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
import org.jenkins.ui.icon.IconType;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;

/**
 * Some utility methods to help working with {@link Icon}, {@link IconSet} and
 * {@link ModelObjectWithContextMenu.ContextMenu}.
 *
 * @since 2.0
 */
@Restricted(NoExternalUse.class)
public class ContextMenuIconUtils {

    /**
     * Prevent instantiation.
     */
    private ContextMenuIconUtils() {
        throw new IllegalAccessError("Utility class");
    }

    /**
     * Combine segments of an URL in the style expected by {@link ModelObjectWithContextMenu.MenuItem}.
     *
     * @param segments the segments.
     * @return the combined URL.
     */
    @NonNull
    public static String buildUrl(String... segments) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (String segment : segments) {
            if (segment == null) {
                continue;
            }
            String str = StringUtils.removeEnd(StringUtils.removeStart(segment, "/"), "/");
            if (str.isEmpty()) {
                continue;
            }
            if (first) {
                first = false;
            } else {
                result.append('/');
            }
            result.append(str);
        }
        return result.toString();
    }

    /**
     * Adds a menu item for the specified action with the supplied prefix offset and optional sub menu.
     *
     * @param menu    the menu to add to.
     * @param prefix  the prefix offset of the action urls.
     * @param action  the action.
     * @param subMenu the sub menu.
     */
    public static void addMenuItem(@NonNull ModelObjectWithContextMenu.ContextMenu menu,
                                   @CheckForNull String prefix, @NonNull Action action,
                                   @CheckForNull ModelObjectWithContextMenu.ContextMenu subMenu) {
        if (isContextMenuVisible(action) && action.getIconFileName() != null) {
            Icon icon = action instanceof IconSpec ? ContextMenuIconUtils.getIcon(action) : null;
            String base = icon != null ? ContextMenuIconUtils.getQualifiedUrl(icon) : Functions.getIconFilePath(action);
            ModelObjectWithContextMenu.MenuItem item = new ModelObjectWithContextMenu.MenuItem(
                    ContextMenuIconUtils.buildUrl(prefix, action.getUrlName()),
                    ContextMenuIconUtils.getMenuItemIconUrl(base),
                    action.getDisplayName()
            );
            item.subMenu = subMenu;
            menu.add(item);
        }
    }
    /** TODO copied from {@link Functions} but currently restricted */
    private static boolean isContextMenuVisible(Action a) {
        if (a instanceof ModelObjectWithContextMenu.ContextMenuVisibility) {
            return ((ModelObjectWithContextMenu.ContextMenuVisibility) a).isVisible();
        } else {
            return true;
        }
    }

    /**
     * Adds a menu item for the specified action with the supplied prefix offset.
     *
     * @param menu   the menu to add to.
     * @param prefix the prefix offset of the action urls.
     * @param action the action.
     */
    public static void addMenuItem(@NonNull ModelObjectWithContextMenu.ContextMenu menu, @CheckForNull String prefix,
                                   @NonNull Action action) {
        addMenuItem(menu, null, action, null);
    }

    /**
     * Adds a menu item for the specified action.
     *
     * @param menu   the menu to add to.
     * @param action the action.
     */
    public static void addMenuItem(@NonNull ModelObjectWithContextMenu.ContextMenu menu, @NonNull Action action) {
        addMenuItem(menu, null, action, null);
    }

    /**
     * Gets the qualified URL for an icon spec.
     *
     * @param spec the icon spec.
     * @return the qualified URL for a menu item
     */
    @CheckForNull
    public static String getMenuItemIconUrlByClassSpec(@CheckForNull String spec) {
        return getMenuItemIconUrl(getQualifiedUrl(getIconByClassSpec(spec)));
    }

    /**
     * Converts a regular icon url into a menu item icon url
     *
     * @param url the "regular" icon url.
     * @return the url for a menu item.
     */
    public static String getMenuItemIconUrl(String url) {
        if (url == null) {
            return null;
        }
        String contextPath = Stapler.getCurrentRequest2().getContextPath();
        return (StringUtils.isBlank(contextPath)? "" : contextPath) + (url.startsWith("images/")
                ? Functions.getResourcePath()
                : "") + (url.startsWith("/") ? url : '/' + url);
    }

    /**
     * Navigate the signature changes in different versions of {@link IconSet}.
     *
     * @param spec the spec to look up.
     * @return the {@link Icon} or {@code null}
     */
    @CheckForNull
    public static Icon getIconByClassSpec(String spec) {
        return IconSet.icons.getIconByClassSpec(spec);
    }

    /**
     * {@link IconSet} support has not been implemented for {@link ModelObjectWithContextMenu.ContextMenu} as of
     * Jenkins 2.5. Hacks to work around
     * some of the fun method signature migrations in {@link IconSet}.
     *
     * @param action the action.
     * @return the {@link Icon} or {@code null}
     */
    @CheckForNull
    public static Icon getIcon(@NonNull Action action) {
        if (action.getIconFileName() == null) {
            return null;
        }
        Icon icon = action instanceof IconSpec
                ? IconSet.icons.getIconByClassSpec(((IconSpec) action).getIconClassName())
                : null;
        if (icon == null) {
            icon = IconSet.icons.getIconByClassSpec(IconSet.toNormalizedIconNameClass(action.getIconFileName()));
        }
        if (icon == null) {
            icon = IconSet.icons.getIconByUrl(action.getIconFileName());
        }
        return icon;
    }

    /**
     * Gets the qualified URL of the specified icon.
     *
     * @param icon the icon.
     * @return the qualified URL of the icon.
     */
    @CheckForNull
    public static String getQualifiedUrl(@CheckForNull Icon icon) {
        if (icon == null) {
            return null;
        }
        try {
            Field iconType = Icon.class.getDeclaredField("iconType");
            iconType.setAccessible(true);
            IconType type = (IconType) iconType.get(icon);
            switch (type) {
                case CORE: {
                    return Functions.getResourcePath() + "/images/" + icon.getUrl();
                }
                case PLUGIN: {
                    return Functions.getResourcePath() + "/plugin/" + icon.getUrl();
                }
            }
            return null;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // ignore we'll use a JellyContext
        }
        JellyContext ctx = new JellyContext();
        ctx.setVariable("resURL", Functions.getResourcePath());
        return icon.getQualifiedUrl(ctx);
    }
}
