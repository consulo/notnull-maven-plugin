package consulo.maven.notNullVerification;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import consulo.maven.notNullVerification.cache.CacheLogic;

/**
 * @author VISTALL
 * @since 10-Jun-17
 */
@Mojo(name = "test-instrument", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES, requiresOnline = false, requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class TestInstrumentMojo extends AbstractInstrumentMojo
{
	@Override
	protected String getTargetDirectory(MavenProject project)
	{
		return project.getBuild().getTestOutputDirectory();
	}

	@Override
	protected String getCacheFileName()
	{
		return CacheLogic.TEST_NAME;
	}
}
