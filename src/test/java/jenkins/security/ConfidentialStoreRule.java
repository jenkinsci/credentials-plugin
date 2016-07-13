package jenkins.security;

import org.junit.rules.ExternalResource;

import org.junit.rules.TemporaryFolder;

/**
 * Test rule that injects a temporary {@link DefaultConfidentialStore}
 *
 * @author Kohsuke Kawaguchi
 */
// TODO remove this when available from the test harness
public class ConfidentialStoreRule extends ExternalResource {
    private final TemporaryFolder tmp = new TemporaryFolder();

    @Override
    protected void before() throws Throwable {
        tmp.create();
        ConfidentialStore.TEST = new ThreadLocal<ConfidentialStore>();
        ConfidentialStore.TEST.set(new DefaultConfidentialStore(tmp.getRoot()));
    }

    @Override
    protected void after() {
        ConfidentialStore.TEST.set(null);
        ConfidentialStore.TEST = null;
        tmp.delete();
    }
}
