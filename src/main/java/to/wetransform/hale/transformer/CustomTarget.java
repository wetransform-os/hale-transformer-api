package to.wetransform.hale.transformer;

import java.util.HashMap;
import java.util.Map;

import eu.esdihumboldt.hale.common.core.io.Value;

public record CustomTarget(String providerId, Map<String, Value> settings) {

    public CustomTarget(String providerId) {
        this(providerId, new HashMap<>());
    }
}
