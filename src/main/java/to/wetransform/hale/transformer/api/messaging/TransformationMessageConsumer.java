package to.wetransform.hale.transformer.api.messaging;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import to.wetransform.hale.transformer.Transformer;
import to.wetransform.hale.transformer.api.TransformerApiApplication;
import to.wetransform.hale.transformer.api.internal.CountdownLatchConfig;

@Service
public class TransformationMessageConsumer {
    /**
     *
     */
    public record TransformationMessage(
            @JsonProperty("projectUrl") String projectUrl, @JsonProperty("sourceDataUrl") String sourceDataUrl)
            implements Serializable {}

    private static final Logger LOG = LoggerFactory.getLogger(TransformationMessageConsumer.class);

    private final CountdownLatchConfig countdownLatchConfig;

    @Autowired
    public TransformationMessageConsumer(CountdownLatchConfig countdownLatchConfig) {
        this.countdownLatchConfig = countdownLatchConfig;
    }

    @RabbitListener(queues = TransformerApiApplication.QUEUE_NAME)
    public void receiveMessage(final TransformationMessage message) {
        LOG.info("Received projectUrl = " + message.projectUrl + "  sourceDataUrl = " + message.sourceDataUrl);

        // TODO Implement mechanism to only accept a message from the queue if no
        // transformation is currently running
        if (message.projectUrl != null && message.sourceDataUrl() != null) {
            Transformer tx = new Transformer();

            try {
                LOG.info("Transformation started");
                tx.transform(message.sourceDataUrl(), message.projectUrl, null);
                tx.getLatch().await(countdownLatchConfig.getWaitingTime(), TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                // TODO What should be done when the transformation fails or times out?
                // - Simply requeuing the message is probably not helpful
                // - Send a message back so that the producer can react?
                Thread.currentThread().interrupt();
                LOG.error("Transformation process timed out: " + e.getMessage(), e);
            }
        }
    }
}
