package to.wetransform.hale.transformer.io.s3;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * Adapter for S3 object storages
 */
public class S3Service implements AutoCloseable {
    private final S3Client s3Client;

    /**
     * Creates an S3 service instance with the given parameters.
     *
     * @param region S3 region to connect to
     * @param credentials Credentials for accessing S3
     * @param endpoint Optional endpoint URL. If null, AWS S3 endpoints are used.
     */
    public S3Service(Region region, AwsCredentials credentials, URI endpoint) {
        S3ClientBuilder builder =
                S3Client.builder().region(region).credentialsProvider(StaticCredentialsProvider.create(credentials));

        if (endpoint != null) {
            builder = builder.endpointOverride(endpoint);
        }

        this.s3Client = builder.build();
    }

    /**
     * Upload a file to an S3 bucket
     *
     * @param bucketName Name of the target bucket
     * @param key Key (file name) of the created object
     * @param file File to upload
     * @return API response
     */
    public PutObjectResponse putObject(String bucketName, String key, File file) {
        PutObjectRequest req =
                PutObjectRequest.builder().bucket(bucketName).key(key).build();

        return s3Client.putObject(req, Path.of(file.toURI()));
    }

    /**
     * Close the S3 connection
     */
    public void close() {
        this.s3Client.close();
    }
}
