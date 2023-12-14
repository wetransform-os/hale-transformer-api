package to.wetransform.hale.transformer;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import eu.esdihumboldt.hale.app.transform.ExecContext;
import eu.esdihumboldt.hale.app.transform.ExecTransformation;
import eu.esdihumboldt.hale.app.transform.ExecUtil;
import eu.esdihumboldt.hale.common.core.HalePlatform;
import eu.esdihumboldt.hale.common.core.io.HaleIO;
import eu.esdihumboldt.hale.common.core.io.IOProvider;
import eu.esdihumboldt.hale.common.core.io.Value;
import eu.esdihumboldt.hale.common.core.io.extension.IOProviderDescriptor;
import eu.esdihumboldt.hale.common.core.io.extension.IOProviderExtension;
import eu.esdihumboldt.hale.common.core.io.project.model.IOConfiguration;
import eu.esdihumboldt.hale.common.core.io.project.model.Project;
import eu.esdihumboldt.hale.common.core.io.supplier.DefaultInputSupplier;
import eu.esdihumboldt.hale.common.core.report.Report;
import eu.esdihumboldt.hale.common.core.report.ReportSession;
import eu.esdihumboldt.hale.common.core.report.util.StatisticsHelper;
import eu.esdihumboldt.hale.common.core.report.writer.ReportReader;
import eu.esdihumboldt.hale.common.instance.io.InstanceIO;
import eu.esdihumboldt.hale.common.instance.io.InstanceWriter;
import eu.esdihumboldt.util.groovy.collector.StatsCollector;
import eu.esdihumboldt.util.io.IOUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.eclipse.core.runtime.content.IContentType;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import to.wetransform.hale.transformer.api.Init;

public class Transformer {

    private static final Logger LOG = LoggerFactory.getLogger(Transformer.class);

    private final CountDownLatch latch = new CountDownLatch(1);

    public void transform(String sourceDataURL, String projectURL, String targetURL) {
        File transformationLogFile = null;

        try {
            Path tempDirectory = createTempDirectory();
            transformationLogFile = createTransformationLogFile(tempDirectory);

            setupLogging(tempDirectory, transformationLogFile);
            File reportFile = createReportFile(tempDirectory);

            LOG.info("Startup...");
            logHeapSize();

            Init.init();
            logPlatformVersion();

            ExecContext context = new ExecContext();

            // Set up project URI
            URI projectUri = new URI(projectURL);
            context.setProject(projectUri);
            // Load project
            Project project = loadProject(projectUri);

            Value sourceCrs = initializeSourceConfig(context, sourceDataURL);

            TargetConfig targetConfig = configureTarget(project, sourceCrs);
            configureTargetContext(context, tempDirectory, targetConfig, reportFile);

            // run the transformation
            LOG.info("Transforming started.");
            new ExecTransformation().run(context);

            // evaluate results
            boolean success = evaluateTransformationResults(reportFile);
            LOG.info("Transformation complete with success = {}", success);
        } catch (Throwable t) {
            LOG.error("Failed to execute transformation: {}", t.getMessage(), t);
        } finally {
            latch.countDown();
            deleteDir(transformationLogFile.getParentFile());
        }
    }

    // Extracted Methods

    private Path createTempDirectory() throws IOException {
        return Files.createTempDirectory("hale-transformer");
    }

    private File createTransformationLogFile(Path tempDirectory) throws IOException {
        File transformationLogFile =
                Files.createTempFile(tempDirectory, "transformation", ".log").toFile();
        transformationLogFile.delete();
        transformationLogFile.createNewFile();
        return transformationLogFile;
    }

    private void setupLogging(Path tempDirectory, File transformationLogFile) throws IOException {
        FileOutputStream transformationLogOut = new FileOutputStream(transformationLogFile, true);
        OutputStream tOut = new TeeOutputStream(System.out, transformationLogOut);
        System.setOut(new PrintStream(tOut));
    }

    private File createReportFile(Path tempDirectory) throws IOException {
        File reportFile = Files.createTempFile(tempDirectory, "reports", ".log").toFile();
        reportFile.delete();
        reportFile.createNewFile();
        return reportFile;
    }

    private void logHeapSize() {
        long heapMaxSize = Runtime.getRuntime().maxMemory();
        LOG.info("Maximum heap size configured as {}", IOUtils.humanReadableByteCount(heapMaxSize, false));
    }

    private void logPlatformVersion() {
        Version version = HalePlatform.getCoreVersion();
        LOG.info("Launching hale-transformer {}...", version);
    }

    private Value initializeSourceConfig(ExecContext context, String sourceDataURL) throws URISyntaxException {
        Map<String, Value> defaultSrs = initializeDefaultSrs();
        SourceConfig sourceConfig = initializeSourceConfig(sourceDataURL, defaultSrs);

        List<SourceConfig> sourceConfigs = new ArrayList<>();
        sourceConfigs.add(sourceConfig);

        context.setSources(sourceConfigs.stream()
                .filter(sourceConfigList -> sourceConfigList.transform())
                .map(sourceConfigList -> sourceConfig.location())
                .collect(Collectors.toList()));
        context.setSourceProviderIds(sourceConfigs.stream()
                .map(sourceConfigList -> sourceConfigList.providerId())
                .collect(Collectors.toList()));
        context.setSourcesSettings(sourceConfigs.stream()
                .map(sourceConfigList -> sourceConfigList.settings())
                .collect(Collectors.toList()));

        return extractedDetectedCRS(sourceConfigs);
    }

    private Value extractedDetectedCRS(List<SourceConfig> sourceConfigs) {
        // extract detected source data crs and use in target config
        Value sourceCrs = null;
        Throwable error = null;
        // try each source config, in case it is not set for some
        for (int i = 0; i < sourceConfigs.size() && (sourceCrs == null || sourceCrs.isEmpty()); i++) {
            try {
                sourceCrs = ((SourceConfig) sourceConfigs.get(i)).settings().get("defaultSrs");
            } catch (Throwable t) {
                error = t;
            }
        }

        if (error != null) {
            LOG.warn("Could not determine source data CRS", error);
        } else if (sourceCrs == null || sourceCrs.isEmpty()) {
            LOG.warn(
                    "Unable to determine source data CRS: None of {} sources is configured with a CRS",
                    sourceConfigs.size());
        }
        return sourceCrs;
    }

    private Map<String, Value> initializeDefaultSrs() {
        Map<String, Value> defaultSrs = new HashMap<>();
        defaultSrs.put("defaultSrs", Value.of("EPSG:4326"));
        defaultSrs.put("xml.pretty", Value.of(true));
        defaultSrs.put("crs.epsg.prefix", Value.of("http://www.opengis.net/def/crs/EPSG/0/"));
        defaultSrs.put("crs", Value.of("code:EPSG:4326"));
        return defaultSrs;
    }

    private SourceConfig initializeSourceConfig(String sourceDataURL, Map<String, Value> defaultSrs)
            throws URISyntaxException {
        return new SourceConfig(
                new URI(sourceDataURL), "eu.esdihumboldt.hale.io.gml.reader", defaultSrs, true, new ArrayList<>());
    }

    private void configureTargetContext(
            ExecContext context, Path tempDirectory, TargetConfig targetConfig, File reportFile)
            throws URISyntaxException {
        File resultDir = new File(tempDirectory.toFile(), "result");
        resultDir.mkdir();

        String targetFilename = targetConfig.filename();
        if (targetFilename == null) {
            targetFilename = "result.out";
        }

        File targetFile = new File(resultDir, targetFilename);
        context.setTarget(targetFile.toURI());

        String preset = targetConfig.preset();
        CustomTarget customConfig = targetConfig.customTarget();
        if (preset != null) {
            context.setPreset(preset);
        } else {
            if (customConfig == null || customConfig.providerId() == null) {
                throw new IllegalStateException("No configuration on how to write transformed data available");
            }
            context.setTargetProviderId(customConfig.providerId());
        }

        if (customConfig != null) {
            context.setTargetSettings(customConfig.settings());
        } else {
            context.setTargetSettings(new HashMap<>());
        }

        context.setReportsOut(reportFile);

        // general configuration
        context.setLogException(true);
    }

    private boolean evaluateTransformationResults(File reportFile) throws IOException {
        // evaluate results TODO to be uncommented
        ReportReader reader = new ReportReader();
        ReportSession reports = reader.readFile(reportFile);
        JSONObject stats = getStats(reports);
        boolean success = evaluateReports(reports.getAllReports().values(), false);

        LOG.info("Transformation complete with success = " + success);
        return success;
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

    private TargetConfig configureTarget(Project lp, Value sourceCrs) {
        String filename;
        String preset = null;
        CustomTarget customTarget = null;

        Map<String, IOConfiguration> presets = getPresets(lp);

        // Preset names
        String defaultPreset = "default";
        String hcPreset = "hale-connect";

        if (presets.containsKey(hcPreset)) {
            // Project contains hale-connect preset
            preset = hcPreset;
            IOConfiguration ioConfiguration = presets.get(hcPreset);
            filename = determineTargetFileName(ioConfiguration);
        } else if (presets.containsKey(defaultPreset)) {
            // Project contains default preset
            preset = defaultPreset;
            IOConfiguration ioConfiguration = presets.get(defaultPreset);
            filename = determineTargetFileName(ioConfiguration);
        } else {
            // No specific presets found, creating a custom target configuration

            Map<String, Value> targetMap = new HashMap<>();

            // Specify target provider for GML FeatureCollection
            String targetProvider = "eu.esdihumboldt.hale.io.gml.xplan.writer"; // "eu.esdihumboldt.hale.io.gml.writer";

            // Additional settings for testing
            targetMap.put("xml.pretty", Value.of(true));
            targetMap.put("crs.epsg.prefix", Value.of("http://www.opengis.net/def/crs/EPSG/0/"));

            // Use CRS from source data analysis if available and a valid EPSG code,
            // otherwise fallback to EPSG:4326
            Value targetCrs =
                    (sourceCrs != null && sourceCrs.getStringRepresentation().startsWith("code:EPSG"))
                            ? sourceCrs
                            : Value.of("code:EPSG:4326");

            targetMap.put("crs", targetCrs);
            LOG.info("Using {} as the transformation target CRS", targetCrs.getStringRepresentation());

            // Create a custom target configuration
            CustomTarget target = new CustomTarget(targetProvider, targetMap);

            filename = "inspire.gml";
            customTarget = target;
        }

        // Create and return the target configuration
        return new TargetConfig(filename, preset, customTarget);
    }

    /**
     * Determine the name of the target file based on an export preset.
     *
     * @param preset the export preset
     * @return the file name for the target file
     */
    public static String determineTargetFileName(IOConfiguration preset) {
        // Default extension to "xml" to reflect old behavior
        String extension = "xml";

        IContentType contentType = determineContentType(preset);
        if (contentType != null) {
            // Derive extension from content type
            String[] extensions = contentType.getFileSpecs(IContentType.FILE_EXTENSION_SPEC);
            if (extensions != null && extensions.length > 0) {
                extension = extensions[0]; // Choose the first one
            }
        }

        // If extension would be "gml," use "xml" instead for backward compatibility
        extension = "gml".equalsIgnoreCase(extension) ? "xml" : extension;

        LOG.info("Chose .{} as the extension for the target file", extension);

        return "result." + extension;
    }

    private static IContentType determineContentType(IOConfiguration preset) {
        // Usually, the content type is part of the settings
        Value value = preset.getProviderConfiguration().get(IOProvider.PARAM_CONTENT_TYPE);
        if (value != null && !value.isEmpty()) {
            return HalePlatform.getContentTypeManager().getContentType(value.as(String.class));
        }

        // Try to determine based on provider ID
        String providerId = preset.getProviderId();
        if (providerId != null) {
            IOProviderDescriptor providerDescriptor =
                    IOProviderExtension.getInstance().getFactory(providerId);
            if (providerDescriptor != null) {
                Set<IContentType> supportedTypes = providerDescriptor.getSupportedTypes();
                if (!supportedTypes.isEmpty()) {
                    IContentType contentType = supportedTypes.iterator().next();

                    if (supportedTypes.size() > 1) {
                        LOG.warn(
                                "Multiple content types as candidates ({}), chose {}",
                                supportedTypes.stream().map(IContentType::getId).collect(Collectors.joining(", ")),
                                contentType.getId());
                    }

                    return contentType;
                }
            }
        }

        return null;
    }

    /**
     * Get all export presets from the project.
     *
     * @param project the hale project object
     * @return the map of presets
     */
    private Map<String, IOConfiguration> getPresets(Project project) {
        Map<String, IOConfiguration> exportPresets = new HashMap<>();

        if (project == null) {
            return exportPresets;
        }

        for (Entry<String, IOConfiguration> entry :
                project.getExportConfigurations().entrySet()) {
            IOConfiguration originalConfiguration = entry.getValue();

            if (InstanceIO.ACTION_SAVE_TRANSFORMED_DATA.equals(originalConfiguration.getActionId())) {
                String presetName = entry.getKey();
                IOConfiguration clonedConfiguration = originalConfiguration.clone();

                // Check and add the I/O provider to exportPresets
                checkAndAddIOProvider(presetName, clonedConfiguration, exportPresets);
            }
        }

        return exportPresets;
    }

    private void checkAndAddIOProvider(
            String presetName, IOConfiguration configuration, Map<String, IOConfiguration> exportPresets) {
        String providerId = configuration.getProviderId();
        IOProviderDescriptor factory = HaleIO.findIOProviderFactory(InstanceWriter.class, null, providerId);

        if (factory != null) {
            String name = Strings.isNullOrEmpty(presetName) ? factory.getDisplayName() : presetName;
            exportPresets.computeIfAbsent(name, k -> configuration);
        } else {
            LOG.error("I/O provider {} for export preset {} not found", providerId, presetName);
        }
    }

    /**
     * After transformation, assemble stats from reports.
     *
     * @param reports the reports
     */
    private JSONObject getStats(ReportSession reports) {
        StatsCollector root =
                new StatisticsHelper().getStatistics(reports.getAllReports().values(), true);

        try {
            return new JSONObject(root.saveToJson(false));
        } catch (JSONException e) {
            LOG.error("Error assembling stats JSON representation", e);
            return null;
        }
    }

    private boolean evaluateReports(Collection<Report<?>> reports, boolean detailed) {
        boolean ok = true;
        ExecUtil.info("Transformation tasks summaries:");

        for (Report<?> report : reports) {
            if (!report.isSuccess() || !report.getErrors().isEmpty()) {
                ok = false;

                ExecUtil.error(report.getTaskName() + ": " + report.getSummary());
                if (detailed) {
                    report.getErrors().forEach(e -> {
                        ExecUtil.error(e.getStackTrace());
                    });
                }
            } else {
                ExecUtil.info(report.getTaskName() + ": " + report.getSummary());
            }
            // TODO process information, provide in a usable way?
        }

        return ok;
    }

    void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDir(f);
            }
        }
        file.delete();
    }

    public CountDownLatch getLatch() {
        return latch;
    }
}
