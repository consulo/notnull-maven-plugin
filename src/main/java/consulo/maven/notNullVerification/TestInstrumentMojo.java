package consulo.maven.notNullVerification;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * @author VISTALL
 * @since 10-Jun-17
 */
@Mojo(name = "test-instrument", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES, requiresOnline = false, requiresDependencyResolution = ResolutionScope.TEST)
public class TestInstrumentMojo extends AbstractInstrumentMojo
{
	@Override
	protected String getTargetDirectory(MavenProject project)
	{
		return project.getBuild().getTestOutputDirectory();
	}
}
