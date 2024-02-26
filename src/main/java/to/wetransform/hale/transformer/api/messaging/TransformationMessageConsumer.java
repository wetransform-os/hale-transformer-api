package to.wetransform.hale.transformer.api.messaging;

import java.io.Serializable;
import java.net.URI;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonProperty;
import eu.esdihumboldt.hale.app.transform.ExecContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.regions.Region;
import to.wetransform.hale.transformer.TargetConfig;
import to.wetransform.hale.transformer.Transformer;
import to.wetransform.hale.transformer.api.TransformerApiApplication;
import to.wetransform.hale.transformer.api.internal.CountdownLatchConfig;
import to.wetransform.hale.transformer.io.s3.S3Service;

@Service
public class TransformationMessageConsumer {
    /**
     *
     */
    public record TransformationMessage(
            @JsonProperty("projectUrl") String projectUrl,
            @JsonProperty("sourceDataUrl") String sourceDataUrl,
            @JsonProperty("targetFileName") String targetFileName,
            @JsonProperty("s3Endpoint") String s3Endpoint,
            @JsonProperty("s3Region") String s3Region,
            @JsonProperty("s3BucketName") String s3BucketName,
            @JsonProperty("s3AccessKey") String s3AccessKey,
            @JsonProperty("s3SecretKey") String s3SecretKey)
            implements Serializable {

        public boolean hasS3Details() {
            return s3Region != null && s3BucketName != null && s3AccessKey != null && s3SecretKey != null;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(TransformationMessageConsumer.class);

    private final CountdownLatchConfig countdownLatchConfig;

    @Autowired
    public TransformationMessageConsumer(CountdownLatchConfig countdownLatchConfig) {
        this.countdownLatchConfig = countdownLatchConfig;
    }

    @RabbitListener(queues = TransformerApiApplication.QUEUE_NAME)
    public void receiveMessage(final TransformationMessage message) {
        LOG.info("Received projectUrl = " + message.projectUrl + "  sourceDataUrl = " + message.sourceDataUrl
                + "  targetFileName = " + message.targetFileName);

        // TODO Implement mechanism to only accept a message from the queue if no
        // transformation is currently running
        if (message.projectUrl != null && message.sourceDataUrl() != null && message.targetFileName != null) {
            Transformer tx = new Transformer();

            try {
                LOG.info("Transformation started");
                tx.transform(message.sourceDataUrl(), message.projectUrl, message.targetFileName);
                tx.getLatch().await(countdownLatchConfig.getWaitingTime(), TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                // TODO What should be done when the transformation fails or times out?
                // - Simply requeuing the message is probably not helpful
                // - Send a message back so that the producer can react?
                Thread.currentThread().interrupt();
                LOG.error("Transformation process timed out: " + e.getMessage(), e);
                return;
            }

            if (message.hasS3Details()) {
                try (S3Service s3 = buildS3Service(message)) {
                    ExecContext execContext = tx.getExecContext();
                    TargetConfig targetConfig = tx.getTargetConfig();
                    if (execContext != null && targetConfig != null) {
                        URI target = execContext.getTarget();
                        s3.putObject(
                                message.s3BucketName,
                                message.targetFileName,
                                Paths.get(target).toFile());
                    }
                } catch (Throwable t) {
                    LOG.error("Error uploading result: " + t.getMessage(), t);
                    // TODO What now? Should the result just be discarded? Should we send a message back?
                }
            }
        }
    }

    private static S3Service buildS3Service(TransformationMessage message) throws IllegalArgumentException {
        URI endpoint = null;
        if (message.s3Endpoint != null) {
            endpoint = URI.create(message.s3Endpoint);
        }

        return new S3Service(
                Region.of(message.s3Region),
                AwsBasicCredentials.create(message.s3AccessKey, message.s3SecretKey),
                endpoint);
    }
}
