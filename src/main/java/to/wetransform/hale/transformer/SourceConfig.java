package to.wetransform.hale.transformer;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.esdihumboldt.hale.common.core.io.Value;

public record SourceConfig(
        URI location, String providerId, Map<String, Value> settings, boolean transform, List<String> attachments) {

    public SourceConfig(URI location, String providerId) {
        this(location, providerId, new HashMap<>(), true, new ArrayList<>());
    }
}
