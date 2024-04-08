package org.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

import javax.inject.Inject;
import java.io.IOException;
import java.io.PrintWriter;

public abstract class MasterExtension {

    private final Project project;

    @Inject
    public MasterExtension(Project project) {
        this.project = project;
    }


    public void setup() {
        final TaskProvider<GreetingGenerator> greetingGeneratorTaskProvider = project.getTasks().register("generateGreeting", GreetingGenerator.class);

        final TaskProvider<Jar> greetingPackagingTaskProvider = project.getTasks().register("packageGreeting", Jar.class, jar -> {
            jar.from(greetingGeneratorTaskProvider.map(GreetingGenerator::getOutput));
            jar.getArchiveFileName().set("greeting.jar");
        });

        final Configuration packagedPublisher = project.getConfigurations().create("greeting", greeting -> {
            greeting.setCanBeConsumed(true);
            greeting.setCanBeResolved(false);

            greeting.getOutgoing().capability(
                    String.format("%s:%s-greeting:%s", project.getGroup(), project.getName(), project.getVersion())
            );

            greeting.getOutgoing().artifact(greetingPackagingTaskProvider.flatMap(Jar::getArchiveFile), artifact -> {
                artifact.setExtension("jar");
                artifact.setType("jar");
                artifact.setClassifier("greeting");
                artifact.builtBy(greetingPackagingTaskProvider);
            });

            greeting.setVisible(true);
            greeting.setDescription("Defines the jar that can be used to build a greeting environment.");
            greeting.attributes(attributes -> {
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.VERSION_CATALOG));
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, "sdk"));
                attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.valueOf(JavaVersion.current().getMajorVersion()));
            });
        });

        AdhocComponentWithVariants javaComponent = (AdhocComponentWithVariants) project.getComponents().getByName("java");
        javaComponent.addVariantsFromConfiguration(packagedPublisher, variant -> variant.mapToOptional());

    }

    public static abstract class GreetingGenerator extends DefaultTask {

        public GreetingGenerator() {
            getOutput().convention(getProject().getLayout().getBuildDirectory().file("greeting.txt"));
        }

        @TaskAction
        public void execute() throws IOException {
            String greeting = "Hello from plugin 'org.example.greeting'";
            getOutput().get().getAsFile().getParentFile().mkdirs();
            getOutput().get().getAsFile().createNewFile();
            try (PrintWriter out = new PrintWriter(getOutput().get().getAsFile())) {
                out.println(greeting);
            }
        }

        @OutputFile
        public abstract RegularFileProperty getOutput();
    }
}
