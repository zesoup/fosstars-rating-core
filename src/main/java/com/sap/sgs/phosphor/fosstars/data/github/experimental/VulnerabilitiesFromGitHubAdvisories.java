package com.sap.sgs.phosphor.fosstars.data.github.experimental;

import static com.sap.sgs.phosphor.fosstars.model.other.Utils.date;
import static com.sap.sgs.phosphor.fosstars.model.value.Vulnerability.UNKNOWN_INTRODUCED_DATE;

import com.sap.sgs.phosphor.fosstars.data.github.CachedSingleFeatureGitHubDataProvider;
import com.sap.sgs.phosphor.fosstars.data.github.experimental.graphql.GitHubAdvisories;
import com.sap.sgs.phosphor.fosstars.data.github.experimental.graphql.data.Advisory;
import com.sap.sgs.phosphor.fosstars.data.github.experimental.graphql.data.AdvisoryReference;
import com.sap.sgs.phosphor.fosstars.data.github.experimental.graphql.data.Node;
import com.sap.sgs.phosphor.fosstars.model.Feature;
import com.sap.sgs.phosphor.fosstars.model.Value;
import com.sap.sgs.phosphor.fosstars.model.feature.oss.VulnerabilitiesInProject;
import com.sap.sgs.phosphor.fosstars.model.value.CVSS;
import com.sap.sgs.phosphor.fosstars.model.value.PackageManager;
import com.sap.sgs.phosphor.fosstars.model.value.Reference;
import com.sap.sgs.phosphor.fosstars.model.value.Vulnerabilities;
import com.sap.sgs.phosphor.fosstars.model.value.Vulnerability;
import com.sap.sgs.phosphor.fosstars.model.value.Vulnerability.Resolution;
import com.sap.sgs.phosphor.fosstars.tool.github.GitHubProject;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

/**
 * This data provider looks for vulnerabilities in {@link GitHubAdvisories} which are not present in
 * {@link NVD}.
 */
public class VulnerabilitiesFromGitHubAdvisories extends CachedSingleFeatureGitHubDataProvider {

  /**
   * A feature that holds info about vulnerabilities in the GitHub Advisory Database.
   */
  public static final Feature<Vulnerabilities> VULNERABILITIES_IN_ADVISORIES =
      new VulnerabilitiesInProject();

  /**
   * An interface to the GitHub Advisory database.
   */
  private final GitHubAdvisories gitHubAdvisories;

  /**
   * Initializes a data provider.
   *
   * @param github An interface to the GitHub API.
   * @param gitHubToken The token to access GitHub API.
   */
  public VulnerabilitiesFromGitHubAdvisories(GitHub github, String gitHubToken) {
    super(github);
    this.gitHubAdvisories = new GitHubAdvisories(gitHubToken);
  }

  @Override
  protected Feature supportedFeature() {
    return VULNERABILITIES_IN_ADVISORIES;
  }

  @Override
  protected Value fetchValueFor(GitHubProject project) throws IOException {
    logger.info("Looking for vulnerabilities from GitHub Advisory ...");

    Vulnerabilities vulnerabilities = new Vulnerabilities();
    // TODO: Make this method recursively loop and gather all config files present in the project
    // and gather the identifiers to pull all possible advisories for the project. More information
    // can be found here https://github.com/SAP/fosstars-rating-core/issues/144
    Optional<String> artifact = artifactFor(gitHubDataFetcher().repositoryFor(project, github));

    if (artifact.isPresent()) {
      for (Node node : gitHubAdvisories.advisoriesFor(PackageManager.MAVEN, artifact.get())) {
        vulnerabilities.add(vulnerabilityFrom(node));
      }
    }
    return VULNERABILITIES_IN_ADVISORIES.value(vulnerabilities);
  }

  /**
   * Gathers groupId:artifactId identifier from the GitHub repository.
   *
   * @param repository The project's repository.
   * @return The artifact identifier.
   * @throws IOException if something goes wrong.
   */
  private Optional<String> artifactFor(GHRepository repository) throws IOException {
    GHContent content;
    try {
      content = repository.getFileContent("pom.xml");
    } catch (GHFileNotFoundException e) {
      logger.warn("Could not find pom.xml!");
      return Optional.empty();
    }

    Model model = readModel(content);

    String groupId =
        model.getGroupId() != null ? model.getGroupId() : model.getParent().getGroupId();
    String artifactId = model.getArtifactId();

    return Optional.ofNullable(String.format("%s:%s", groupId, artifactId));
  }

  /**
   * Parses a pom.xml file.
   *
   * @param content The content of the pom.xml file.
   * @return A {@link Model} which represents the pom.xml file.
   * @throws IOException If something went wrong.
   */
  private static Model readModel(GHContent content) throws IOException {
    try (InputStream is = content.read()) {
      try {
        return new MavenXpp3Reader().read(is);
      } catch (XmlPullParserException e) {
        throw new IOException(e);
      }
    }
  }

  /**
   * Convert List of {@link AdvisoryReference} to List of {@link Reference}.
   * 
   * @param references List of type {@link AdvisoryReference}.
   * @return List of {@link Reference}.
   */
  private List<Reference> referencesFrom(List<AdvisoryReference> references) {
    List<Reference> referenceList = new ArrayList<>();
    for (AdvisoryReference r : references) {
      try {
        referenceList.add(new Reference(null, new URL(r.getUrl())));
      } catch (MalformedURLException e) {
        System.out.println("Could not parse a URL from a reference in NVD");
      }
    }
    return referenceList;
  }

  /**
   * Converts an {@link Node} to a {@link Vulnerability}.
   *
   * @param entry The {@link Node} to be converted.
   * @return An instance of {@link Vulnerability}.
   */
  private Vulnerability vulnerabilityFrom(Node node) {
    Advisory advisory = node.getAdvisory();

    String id = advisory.getGhsaId();
    String description = advisory.getDescription();
    CVSS cvss = CVSS.UNKNOWN;
    List<Reference> references = referencesFrom(advisory.getReferences());

    Resolution resolution =
        node.getFirstPatchedVersion() != null ? Resolution.PATCHED : Resolution.UNPATCHED;

    Date fixed = date(advisory.getPublishedAt());
    return new Vulnerability(id, description, cvss, references, resolution, UNKNOWN_INTRODUCED_DATE,
        fixed);
  }
}