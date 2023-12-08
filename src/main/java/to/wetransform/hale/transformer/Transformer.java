package to.wetransform.hale.transformer;

import java.io.InputStream;
import java.net.URI;
import java.text.MessageFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import eu.esdihumboldt.hale.app.transform.ExecContext;
import eu.esdihumboldt.hale.common.core.HalePlatform;
import eu.esdihumboldt.hale.common.core.io.project.model.Project;
import eu.esdihumboldt.hale.common.core.io.supplier.DefaultInputSupplier;
import eu.esdihumboldt.util.io.IOUtils;
import org.osgi.framework.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import to.wetransform.halecli.internal.Init;

public class Transformer {

    private static final Logger LOG = LoggerFactory.getLogger(Transformer.class);

    private CountDownLatch latch = new CountDownLatch(1);

    public void transform(/* TODO add parameters for data and project sources */ ) {
        // TODO setup log files for reports and transformation log

        long heapMaxSize = Runtime.getRuntime().maxMemory();
        LOG.info("Maximum heap size configured as " + IOUtils.humanReadableByteCount(heapMaxSize, false));

        Init.init();

        Version version = HalePlatform.getCoreVersion();
        LOG.info(MessageFormat.format("Launching hale-transformer {0}...", version.toString()));

        ExecContext context = new ExecContext();

        // URI projectUri = ....
        // context.setProject(projectUri);
        // Project project = loadProject(projectUri);

        // context.setSources(...)
        // context.setSourceProviderIds(...)
        // context.setSourcesSettings(...)

        // Value sourceCrs = null;
        // TODO determine source CRS

        // TargetConfig targetConfig = configureTarget(project, sourceCrs);

        try {
            // run the transformation

            LOG.info("Transforming...");
            TimeUnit.SECONDS.sleep(30);
            // new ExecTransformation().run(context);

            LOG.info("Transformation complete.");
        } catch (Throwable t) {
            LOG.error("Failed to execute transformation: " + t.getMessage(), t);
        } finally {
            latch.countDown();
        }
    }

    private Project loadProject(URI projectUri) {
        DefaultInputSupplier supplier = new DefaultInputSupplier(projectUri);
        Project result = null;
        try (InputStream in = supplier.getInput()) {
            result = Project.load(in);
        } catch (Exception e) {
            LOG.warn("Could not load project file to determine presets: " + e.getStackTrace());
        }
        return result;
    }

    public CountDownLatch getLatch() {
        return latch;
    }
}
