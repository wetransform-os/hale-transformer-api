package to.wetransform.hale.transformer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;

public class RunContext {

    private final List<Path> tempFiles = new ArrayList<>();

    public File createTempDir() throws IOException {
        Path path = Files.createTempDirectory("hale-transformer");
        tempFiles.add(path);
        return path.toFile();
    }

    public void cleanUp() throws IOException {
        for (Path path : tempFiles) {
            FileUtils.deleteDirectory(path.toFile());
        }
        tempFiles.clear();
    }
}
