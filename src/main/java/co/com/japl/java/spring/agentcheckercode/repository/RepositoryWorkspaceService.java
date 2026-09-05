package co.com.japl.java.spring.agentcheckercode.repository;

import co.com.japl.java.spring.agentcheckercode.config.RepositoriesConfigProperties.RepositoryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RepositoryWorkspaceService {

    private static final long GIT_COMMAND_TIMEOUT_MINUTES = 5;

    public Path prepareRepository(RepositoryConfig repository, String basePath) {
        if (repository == null || repository.getGithub() == null
                || repository.getGithub().getOwner() == null || repository.getGithub().getRepo() == null) {
            return null;
        }

        Path repositoryPath = resolveBasePath(basePath).resolve(repository.getGithub().getRepo());
        try {
            if (Files.isDirectory(repositoryPath.resolve(".git"))) {
                runGit(repositoryPath, "pull", "--ff-only");
            } else {
                Files.createDirectories(repositoryPath.getParent());
                runGit(repositoryPath.getParent(), "clone", "--depth", "1",
                        "https://github.com/" + repository.getGithub().getOwner()
                                + "/" + repository.getGithub().getRepo() + ".git",
                        repositoryPath.getFileName().toString());
            }
            log.info("Repository '{}' is available at '{}'.", repository.getName(), repositoryPath);
            return repositoryPath;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Unable to prepare local repository '{}' at '{}': {}",
                    repository.getName(), repositoryPath, e.getMessage());
            return Files.isDirectory(repositoryPath) ? repositoryPath : null;
        }
    }

    public String readFile(Path repositoryPath, String relativePath) throws IOException {
        if (repositoryPath == null || relativePath == null || relativePath.isBlank()) {
            return "";
        }
        Path file = repositoryPath.resolve(relativePath).normalize();
        if (!file.startsWith(repositoryPath.normalize()) || !Files.isRegularFile(file)) {
            return "";
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private Path resolveBasePath(String basePath) {
        String configuredPath = basePath == null || basePath.isBlank() ? "./repositories" : basePath;
        if (configuredPath.equals("~")) {
            configuredPath = System.getProperty("user.home");
        } else if (configuredPath.startsWith("~/") || configuredPath.startsWith("~\\")) {
            configuredPath = Paths.get(System.getProperty("user.home"), configuredPath.substring(2)).toString();
        }
        return Paths.get(configuredPath).toAbsolutePath().normalize();
    }

    private void runGit(Path workingDirectory, String... arguments) throws IOException, InterruptedException {
        String[] command = new String[arguments.length + 1];
        command[0] = "git";
        System.arraycopy(arguments, 0, command, 1, arguments.length);

        Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        if (!process.waitFor(GIT_COMMAND_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IOException("Git command timed out");
        }
        if (process.exitValue() != 0) {
            throw new IOException("Git command failed with exit code " + process.exitValue());
        }
    }
}