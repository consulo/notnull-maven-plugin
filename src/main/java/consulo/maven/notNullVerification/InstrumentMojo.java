package consulo.maven.notNullVerification;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * @author VISTALL
 * @since 28-May-17
 */
@Mojo(name = "instrument", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresOnline = false, requiresDependencyResolution = ResolutionScope.COMPILE)
public class InstrumentMojo extends AbstractInstrumentMojo
{
	@Override
	protected String getTargetDirectory(MavenProject project)
	{
		return project.getBuild().getOutputDirectory();
	}
}
