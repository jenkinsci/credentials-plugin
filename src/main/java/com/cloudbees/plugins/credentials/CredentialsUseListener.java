package com.cloudbees.plugins.credentials;

import hudson.ExtensionPoint;
import hudson.model.Item;
import hudson.model.Node;
import hudson.model.Run;
import jenkins.util.Listeners;

import java.util.List;


/**
 * A Listener to track {@link Credentials} usage.
 * @see CredentialsProvider#trackAll(Item, List) 
 * @see CredentialsProvider#trackAll(Run, List) 
 * @see CredentialsProvider#trackAll(Node, List) 
 */
public interface CredentialsUseListener extends ExtensionPoint {

    /**
     * Called when {@link Credentials} is read by a run.
     *
     * @param c   The used Credentials.
     * @param run The run using the credentials.
     */
    void onUse(Credentials c, Run run);

    /**
     * Called when {@link Credentials} is read by a node.
     *
     * @param c   The used Credentials.
     * @param node The node using the credentials.
     */
    void onUse(Credentials c, Node node);

    /**
     * Called when {@link Credentials} is read by an item.
     *
     * @param c   The used Credentials.
     * @param item The item using the credentials.
     */
    void onUse(Credentials c, Item item);

    /**
     * Fires the {@link #onUse} event to track the run that uses credentials.
     *  @see CredentialsProvider#trackAll(Run, List)
     */
    static void fireUse(Credentials c, Run run) {
        Listeners.notify(CredentialsUseListener.class, true, listener -> listener.onUse(c, run));
    }

    /**
     * Fires the {@link #onUse} event to track the node that uses credentials.
     *  @see CredentialsProvider#trackAll(Node, List)
     */
    static void fireUse(Credentials c, Node node) {
        Listeners.notify(CredentialsUseListener.class, true, listener -> listener.onUse(c, node));
    }

    /**
     * Fires the {@link #onUse} event to track the item that uses credentials.
     *  @see CredentialsProvider#trackAll(Item, List)
     */
    static void fireUse(Credentials c, Item item) {
        Listeners.notify(CredentialsUseListener.class, true, listener -> listener.onUse(c, item));
    }
}
