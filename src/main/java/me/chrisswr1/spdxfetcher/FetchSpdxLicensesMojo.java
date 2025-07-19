package me.chrisswr1.spdxfetcher;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mojo(name = "fetch-spdx-licenses", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class FetchSpdxLicensesMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project.build.directory}/generated-resources/licenses.xml", readonly = true)
    private File licensesFile;
    @Parameter(defaultValue = "${project.build.directory}/generated-resources/spdx-licenses", readonly = true)
    private File outputDirectory;

    private final Pattern SPDX_PATTERN = Pattern.compile("^.+ \\((?<spdx>[A-Z][\\w.\\-+]+)\\)$");

    @Override
    public void execute() throws MojoExecutionException {
        if (!licensesFile.exists()) {
            this.getLog().error("licenses.xml not found: " + licensesFile.getAbsolutePath());
            return;
        }

        Set<String> spdxSet = new TreeSet<>();

        this.getLog().info("Parsing license file as XML: " + licensesFile.getAbsolutePath());

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(false);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(licensesFile);
            doc.getDocumentElement().normalize();

            NodeList licenseNodes = doc.getElementsByTagName("license");

            for (int i = 0; i < licenseNodes.getLength(); i++) {
                Element licenseElem = (Element) licenseNodes.item(i);
                NodeList nameNodes = licenseElem.getElementsByTagName("name");
                if (nameNodes.getLength() > 0) {
                    String licenseName = nameNodes.item(0).getTextContent().trim();
                    Matcher matcher = SPDX_PATTERN.matcher(licenseName);
                    if (matcher.matches()) {
                        String spdxId = matcher.group("spdx");
                        if (spdxId != null) {
                            spdxSet.add(spdxId);
                        }
                    } else {
                        this.getLog().warn("Could not extract SPDX ID from license name: " + licenseName);
                    }
                }
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to parse licenses.xml", e);
        }

        // Download all SPDX texts
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new MojoExecutionException("Could not create SPDX output directory: " + outputDirectory);
        }

        for (String spdx : spdxSet) {
            String url = "https://raw.githubusercontent.com/spdx/license-list-data/main/text/" + spdx + ".txt";
            File outFile = new File(outputDirectory, spdx + ".txt");
            this.getLog().info("Downloading SPDX license: " + spdx);
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                this.getLog().warn("Failed to download SPDX text for " + spdx + ": " + e.getMessage());
            }
        }

        this.getLog().info("Downloaded " + spdxSet.size() + " SPDX license texts.");
    }
}
