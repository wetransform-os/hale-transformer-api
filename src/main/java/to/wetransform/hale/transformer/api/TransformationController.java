package to.wetransform.hale.transformer.api;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransformationController {

    private static final Logger log = LoggerFactory.getLogger(TransformationController.class);

    private static final String TRANSFORMER_IMAGE = "wetransform/hale-transformer:latest"; // TODO Should be configurable

    // TODO Adapt endpoints to OGC API Processes
    @GetMapping("/transform")
    public void transform() {
        String containerId;
        WaitContainerResultCallback callback;

        DockerClientConfig std = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(std.getDockerHost())
                .build();

        DockerClient docker = DockerClientImpl.getInstance(std, httpClient);

        // TODO Implement container start here
        // Data and project source must be configurable (in first step as URLs)
        // If transformation project get baked into the Docker image later on, configuration could
        // be done e.g. via an identifier

        // Dummy Docker operation "getId" -> replace with logic to run transformer image
        containerId = docker.createContainerCmd(TRANSFORMER_IMAGE)
                .withEnv("HALE_OPTS=-Dlog.hale.level=INFO -Dlog.root.level=WARN -Xmx800m",
                        "HT_PROJECT_URL=https://wetransform.box.com/shared/static/pvtiecxvpuuo061t7mrmuu8iknrj84oj.halez",
                        "HT_SOURCE_URL=https://wetransform.box.com/shared/static/gub5gnfv7wljekwwglo33on58a2w6gk3.gml")
                .exec().getId();

        LogContainerCmd logCmd = docker.logContainerCmd(containerId);
        logCmd.withStdOut(true).withStdErr(true).withTimestamps(true);

        log.info(docker.inspectImageCmd(TRANSFORMER_IMAGE).exec().getCreated());

        docker.startContainerCmd(containerId).exec();
    }
}
