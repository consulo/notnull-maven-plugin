package consulo.maven.notNullVerification;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import consulo.maven.notNullVerification.cache.CacheLogic;

/**
 * @author VISTALL
 * @since 10-Jun-17
 */
public abstract class AbstractInstrumentMojo extends AbstractMojo
{
	@Parameter(property = "project", defaultValue = "${project}")
	private MavenProject myMavenProject;

	protected abstract String getTargetDirectory(MavenProject project);

	protected abstract List<String> getClasspathElements(MavenProject project) throws DependencyResolutionRequiredException;

	protected abstract String getCacheFileName();

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		try
		{
			String outputDirectory = getTargetDirectory(myMavenProject);
			File directory = new File(outputDirectory);
			if(!directory.exists())
			{
				getLog().info(outputDirectory + " is not exists");
				return;
			}

			getLog().info(outputDirectory);
			List<File> files = FileUtils.getFiles(directory, "**/*.class", null);
			if(files.isEmpty())
			{
				return;
			}

			if(isJdk9())
			{
				getLog().info("Target: java 9");
			}

			InstrumentationClassFinder finder = buildFinder();

			CacheLogic cacheLogic = new CacheLogic(myMavenProject, getCacheFileName());

			cacheLogic.read();

			boolean changed = false;
			for(File classFile : files)
			{
				if(cacheLogic.isUpToDate(classFile))
				{
					continue;
				}

				cacheLogic.removeCacheEntry(classFile);

				byte[] data;
				FileInputStream stream = null;
				try
				{
					stream = new FileInputStream(classFile);
					data = IOUtil.toByteArray(stream);
				}
				catch(Exception e)
				{
					getLog().error(e);
					continue;
				}
				finally
				{
					IOUtil.close(stream);
				}

				try
				{
					FailSafeClassReader reader = new FailSafeClassReader(data, 0, data.length);

					ClassWriter writer = new InstrumenterClassWriter(reader, isJdk6() ? ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS, finder);

					NotNullVerifyingInstrumenter.processClassFile(reader, writer);

					changed = true;

					FileOutputStream outputStream = null;
					try
					{
						outputStream = new FileOutputStream(classFile);
						outputStream.write(writer.toByteArray());
					}
					finally
					{
						IOUtil.close(outputStream);
					}

					getLog().debug("Processed: " + classFile.getPath());

					cacheLogic.putCacheEntry(new File(classFile.getPath()));
				}
				catch(Exception e)
				{
					getLog().error("Fail to instrument " + classFile.getPath(), e);
				}
			}

			if(!changed)
			{
				getLog().info("Nothing to instrument - all classes are up to date");
			}
			else
			{
				cacheLogic.write();
			}
		}
		catch(Exception e)
		{
			getLog().error(e);
		}
	}

	private InstrumentationClassFinder buildFinder() throws MalformedURLException, DependencyResolutionRequiredException
	{
		Collection<URL> classpath = new LinkedHashSet<URL>();
		addParentClasspath(classpath, false);
		addParentClasspath(classpath, true);

		File javaHome = new File(System.getProperty("java.home"));

		File rtJar = new File(javaHome, "lib/rt.jar");
		if(rtJar.exists())
		{
			classpath.add(rtJar.toURI().toURL());
		}

		for(String compileClasspathElement : getClasspathElements(myMavenProject))
		{
			classpath.add(new File(compileClasspathElement).toURI().toURL());
		}

		boolean jdk9 = isJdk9();
		URL[] platformUrls = new URL[jdk9 ? 1 : 0];
		if(jdk9)
		{
			platformUrls[0] = InstrumentationClassFinder.createJDKPlatformUrl(javaHome.getPath());
		}
		return new InstrumentationClassFinder(platformUrls, classpath.toArray(new URL[classpath.size()]));
	}

	private boolean isJdk6()
	{
		// FIXME [VISTALL] we need this?
		return true;
	}

	private boolean isJdk9()
	{
		try
		{
			int version = Integer.parseInt(System.getProperty("java.version"));
			return version >= 9;
		}
		catch(Exception ignored)
		{
		}
		return false;
	}

	protected File getClassFile(String className)
	{
		final String classOrInnerName = getClassOrInnerName(className);
		if(classOrInnerName == null)
		{
			return null;
		}
		return new File(myMavenProject.getBuild().getOutputDirectory(), classOrInnerName + ".class");
	}

	protected String getClassOrInnerName(String className)
	{
		File classFile = new File(myMavenProject.getBuild().getOutputDirectory(), className + ".class");
		if(classFile.exists())
		{
			return className;
		}
		int position = className.lastIndexOf('/');
		if(position == -1)
		{
			return null;
		}
		return getClassOrInnerName(className.substring(0, position) + '$' + className.substring(position + 1));
	}

	private void addParentClasspath(Collection<URL> classpath, boolean ext) throws MalformedURLException
	{
		boolean isJava9 = isJdk9();
		if(!isJava9)
		{
			String[] extDirs = System.getProperty("java.ext.dirs", "").split(File.pathSeparator);
			if(ext && extDirs.length == 0)
			{
				return;
			}

			List<URLClassLoader> loaders = new ArrayList<URLClassLoader>(2);
			for(ClassLoader loader = InstrumentMojo.class.getClassLoader(); loader != null; loader = loader.getParent())
			{
				if(loader instanceof URLClassLoader)
				{
					loaders.add(0, (URLClassLoader) loader);
				}
				else
				{
					getLog().warn("Unknown class loader: " + loader.getClass().getName());
				}
			}

			for(URLClassLoader loader : loaders)
			{
				URL[] urls = loader.getURLs();
				for(URL url : urls)
				{
					String path = urlToPath(url);

					boolean isExt = false;
					for(String extDir : extDirs)
					{
						if(path.startsWith(extDir) && path.length() > extDir.length() && path.charAt(extDir.length()) == File.separatorChar)
						{
							isExt = true;
							break;
						}
					}

					if(isExt == ext)
					{
						classpath.add(url);
					}
				}
			}
		}
		else if(!ext)
		{
			parseClassPathString(ManagementFactory.getRuntimeMXBean().getClassPath(), classpath);
		}
	}

	private void parseClassPathString(String pathString, Collection<URL> classpath)
	{
		if(pathString != null && !pathString.isEmpty())
		{
			try
			{
				StringTokenizer tokenizer = new StringTokenizer(pathString, File.pathSeparator + ',', false);
				while(tokenizer.hasMoreTokens())
				{
					String pathItem = tokenizer.nextToken();
					classpath.add(new File(pathItem).toURI().toURL());
				}
			}
			catch(MalformedURLException e)
			{
				getLog().error(e);
			}
		}
	}

	private static String urlToPath(URL url) throws MalformedURLException
	{
		try
		{
			return new File(url.toURI().getSchemeSpecificPart()).getPath();
		}
		catch(URISyntaxException e)
		{
			throw new MalformedURLException(url.toString());
		}
	}
}
