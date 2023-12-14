package to.wetransform.hale.transformer.api;

import groovy.lang.GroovySystem;
import org.eclipse.equinox.nonosgi.registry.RegistryFactoryHelper;
import org.slf4j.bridge.SLF4JBridgeHandler;
import to.wetransform.hale.transformer.api.internal.CustomMetaClassCreationHandle;

public class Init {

    public static void init() {
        SLF4JBridgeHandler.install();

        // initialize registry
        RegistryFactoryHelper.getRegistry();

        // initialize meta extensions
        GroovySystem.getMetaClassRegistry().setMetaClassCreationHandle(new CustomMetaClassCreationHandle());
    }
}
