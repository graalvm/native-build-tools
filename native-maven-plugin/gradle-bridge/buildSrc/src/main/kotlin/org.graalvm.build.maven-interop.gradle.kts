/**
 * A conventional plugin which sets up Maven interoperability,
 * by allowing to declare "tasks" which bridge to maven goals
 */
import org.graalvm.build.maven.MavenExtension

val extension = extensions.create("maven", MavenExtension::class.java, tasks, project.layout.projectDirectory)

