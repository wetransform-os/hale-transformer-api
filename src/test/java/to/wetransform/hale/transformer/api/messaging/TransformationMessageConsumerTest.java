package to.wetransform.hale.transformer.api.messaging;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TransformationMessageConsumerTest {

    @Test
    void testDeserializeTransformationMessage() throws Exception {
        // Mock the received message
        byte[] messageBody =
                "{\"projectUrl\": \"https://example.org/example.halez\", \"sourceDataUrl\": \"https://example.org/example.gml\", \"targetFileName\": \"result.gml\", \"s3Region\": \"eu-west-1\", \"s3BucketName\": \"example-bucket\", \"s3AccessKey\": \"ACCESSKEY\", \"s3SecretKey\": \"SECRETKEY\"}"
                        .getBytes();

        ObjectMapper mapper = new ObjectMapper();
        TransformationMessageConsumer.TransformationMessage transformationMessage =
                mapper.readValue(messageBody, TransformationMessageConsumer.TransformationMessage.class);

        assertEquals("https://example.org/example.halez", transformationMessage.projectUrl());
        assertEquals("https://example.org/example.gml", transformationMessage.sourceDataUrl());
        assertEquals("result.gml", transformationMessage.targetFileName());
        assertEquals("eu-west-1", transformationMessage.s3Region());
        assertEquals("example-bucket", transformationMessage.s3BucketName());
        assertEquals("ACCESSKEY", transformationMessage.s3AccessKey());
        assertEquals("SECRETKEY", transformationMessage.s3SecretKey());
    }
}
