package org.example;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.*;

import javax.inject.Inject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public abstract class TargetExtension {

    private final Project project;

    @Inject
    public TargetExtension(Project project) {
        this.project = project;
    }

    public void setup() {
        final ModuleDependency packagedGreeting = (ModuleDependency) project.getDependencies().create("org.example:master:0.1.0-SNAPSHOT");
        packagedGreeting.capabilities(capabilities -> capabilities.requireCapability("org.example:master-greeting:0.1.0-SNAPSHOT"));

        final Configuration resolveGreeting = project.getConfigurations().create("greeting");
        resolveGreeting.getDependencies().add(packagedGreeting);

        final ResolvedConfiguration resolvedConfiguration = resolveGreeting.getResolvedConfiguration();
        final Set<ResolvedArtifact> resolvedArtifacts = resolvedConfiguration.getResolvedArtifacts();
        if (resolvedArtifacts.size() != 1) {
            throw new IllegalStateException("Expected 1 artifact, but found " + resolvedArtifacts.size());
        }

        final ResolvedArtifact resolvedArtifact = resolvedArtifacts.iterator().next();
        final File file = resolvedArtifact.getFile();

        project.getLogger().lifecycle("Resolved greeting artifact to: " + file.getAbsolutePath());

        try {
            processFileFromZip(file, "greeting.txt", in -> {
                try {
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        System.out.write(buffer, 0, read);
                    }
                    return null;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public static <T> T processFileFromZip(File zipArchivePath, String pathInArchive, ThrowingFunction<InputStream, T> processor) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipArchivePath)) {
            ZipEntry entry = zipFile.getEntry(pathInArchive);
            if (entry == null) {
                throw new FileNotFoundException("Couldn't find " + pathInArchive + " in " + zipArchivePath);
            }

            try (InputStream in = zipFile.getInputStream(entry)) {
                return processor.apply(in);
            } catch (Throwable e) {
                throw new IOException("Failed to process file " + pathInArchive + " from " + zipArchivePath);
            }
        }
    }

    @FunctionalInterface
    public interface ThrowingFunction<T, S> {
        S apply(T t) throws Throwable;
    }
}
