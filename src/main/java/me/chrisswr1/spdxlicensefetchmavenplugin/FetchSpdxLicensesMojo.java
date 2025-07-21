package me.chrisswr1.spdxlicensefetchmavenplugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import proguard.annotation.Keep;
import proguard.annotation.KeepName;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mojo(
	name = "fetch-spdx-licenses",
	defaultPhase = LifecyclePhase.PROCESS_RESOURCES,
	threadSafe = true
)
@Keep
public class FetchSpdxLicensesMojo extends AbstractMojo {
	@Parameter(
		defaultValue = "${project.build.directory}/generated-resources/licenses.xml",
		readonly = true
	)
	@KeepName
	private File licensesFile;
	@Parameter(
		defaultValue = "${project.build.directory}/generated-resources/spdx-licenses",
		readonly = true
	)
	@KeepName
	private File outputDirectory;
	@Parameter(
		defaultValue = "https://raw.githubusercontent.com/spdx/license-list-data/main/text/{license.id}.txt",
		readonly = true
	)
	@KeepName
	private String licensesUrl;

	private final Pattern SPDX_PATTERN = Pattern.compile("^.+ \\((?<spdx>[A-Z][\\w.\\-+]+)\\)$");

	@Override
	public void execute() throws MojoExecutionException {
		if (!licensesFile.exists()) {
			this.getLog().error("licenses.xml not found: " + licensesFile.getAbsolutePath());
			return;
		}

		final @NotNull Set<String> spdxSet = new TreeSet<>();

		this.getLog().info("Parsing license file as XML: " + licensesFile.getAbsolutePath());

		try {
			final @NotNull DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
			docBuilderFactory.setNamespaceAware(false);
			final @NotNull DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
			final @NotNull Document doc = docBuilder.parse(licensesFile);
			doc.getDocumentElement().normalize();

			final @NotNull NodeList licenseNodes = doc.getElementsByTagName("license");
			for (int i = 0; i < licenseNodes.getLength(); i++) {
				final @NotNull Element licenseElement = (Element) licenseNodes.item(i);
				final @NotNull NodeList nameNodes = licenseElement.getElementsByTagName("name");
				if (nameNodes.getLength() > 0) {
					final @NotNull String licenseName = nameNodes.item(0).getTextContent().trim();
					final @NotNull Matcher matcher = SPDX_PATTERN.matcher(licenseName);
					if (matcher.matches()) {
						final @NotNull String spdx = matcher.group("spdx");
						if (spdx != null) {
							spdxSet.add(spdx);
						}
					} else {
						this.getLog().warn("Could not extract SPDX ID from license name: " + licenseName);
					}
				}
			}
		} catch (final @NotNull IOException | SAXException | ParserConfigurationException e) {
			throw new MojoExecutionException("Failed to parse licenses.xml", e);
		}

		if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
			throw new MojoExecutionException("Could not create SPDX output directory: " + outputDirectory);
		}

		for (String spdx : spdxSet) {
			try {
				final @NotNull URL url = new URL(this.licensesUrl.replace("{license.id}", spdx));
				final @Nullable Path filenamePath = Paths.get(url.getPath()).getFileName();
				if (filenamePath == null) {
					throw new IOException("Cloud not determine filename from URL: " + url.toString());
				}
				final @NotNull String filename = filenamePath.toString();
				final @NotNull File outFile = new File(outputDirectory, filename);
				this.getLog().info("Downloading SPDX license: " + spdx);
				final @NotNull InputStream in = url.openStream();
				Files.copy(in, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (final @NotNull IOException e) {
				this.getLog().warn("Failed to download SPDX text for " + spdx + ": " + e.getMessage());
			}
		}

		this.getLog().info("Downloaded " + spdxSet.size() + " SPDX license texts.");
	}
}
