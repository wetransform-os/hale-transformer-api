package to.wetransform.hale.transformer.api.messaging;

import java.io.Serializable;
import java.util.concurrent.CountDownLatch;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import to.wetransform.hale.transformer.Transformer;
import to.wetransform.hale.transformer.api.TransformerApiApplication;

@Service
public class TransformationMessageConsumer {
    /**
     *
     */
    public record TransformationMessage(
            @JsonProperty("projectUrl") String projectUrl, @JsonProperty("sourceDataUrl") String sourceDataUrl)
            implements Serializable {}

    private static final Logger LOG = LoggerFactory.getLogger(TransformationMessageConsumer.class);

    private CountDownLatch latch = new CountDownLatch(1);

    @RabbitListener(queues = TransformerApiApplication.QUEUE_NAME)
    public void receiveMessage(final TransformationMessage message) {
        LOG.info("Received projectUrl = " + message.projectUrl + "  sourceDataUrl = " + message.sourceDataUrl);
        latch.countDown();
    }

    public CountDownLatch getLatch() {
        return latch;
    }
}
