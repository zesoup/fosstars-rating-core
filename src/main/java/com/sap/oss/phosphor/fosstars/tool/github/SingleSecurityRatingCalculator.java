package com.sap.oss.phosphor.fosstars.tool.github;

import com.sap.oss.phosphor.fosstars.data.DataProvider;
import com.sap.oss.phosphor.fosstars.data.github.CodeqlDataProvider;
import com.sap.oss.phosphor.fosstars.data.github.FuzzedInOssFuzz;
import com.sap.oss.phosphor.fosstars.data.github.GitHubDataFetcher;
import com.sap.oss.phosphor.fosstars.data.github.HasBugBountyProgram;
import com.sap.oss.phosphor.fosstars.data.github.HasCompanySupport;
import com.sap.oss.phosphor.fosstars.data.github.HasSecurityPolicy;
import com.sap.oss.phosphor.fosstars.data.github.HasSecurityTeam;
import com.sap.oss.phosphor.fosstars.data.github.InfoAboutVulnerabilities;
import com.sap.oss.phosphor.fosstars.data.github.IsApache;
import com.sap.oss.phosphor.fosstars.data.github.IsEclipse;
import com.sap.oss.phosphor.fosstars.data.github.LgtmDataProvider;
import com.sap.oss.phosphor.fosstars.data.github.NumberOfCommits;
import com.sap.oss.phosphor.fosstars.data.github.NumberOfContributors;
import com.sap.oss.phosphor.fosstars.data.github.NumberOfStars;
import com.sap.oss.phosphor.fosstars.data.github.NumberOfWatchers;
import com.sap.oss.phosphor.fosstars.data.github.OwaspSecurityLibraries;
import com.sap.oss.phosphor.fosstars.data.github.PackageManagement;
import com.sap.oss.phosphor.fosstars.data.github.ProgrammingLanguages;
import com.sap.oss.phosphor.fosstars.data.github.SignsJarArtifacts;
import com.sap.oss.phosphor.fosstars.data.github.UsesDependabot;
import com.sap.oss.phosphor.fosstars.data.github.UsesFindSecBugs;
import com.sap.oss.phosphor.fosstars.data.github.UsesGithubForDevelopment;
import com.sap.oss.phosphor.fosstars.data.github.UsesNoHttpTool;
import com.sap.oss.phosphor.fosstars.data.github.UsesOwaspDependencyCheck;
import com.sap.oss.phosphor.fosstars.data.github.UsesSanitizers;
import com.sap.oss.phosphor.fosstars.data.github.UsesSignedCommits;
import com.sap.oss.phosphor.fosstars.data.interactive.AskAboutSecurityTeam;
import com.sap.oss.phosphor.fosstars.data.interactive.AskAboutUnpatchedVulnerabilities;
import com.sap.oss.phosphor.fosstars.model.RatingRepository;
import com.sap.oss.phosphor.fosstars.model.ValueSet;
import com.sap.oss.phosphor.fosstars.model.rating.oss.OssSecurityRating;
import com.sap.oss.phosphor.fosstars.model.subject.oss.GitHubProject;
import com.sap.oss.phosphor.fosstars.model.value.ValueHashSet;
import com.sap.oss.phosphor.fosstars.nvd.NVD;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The class calculates a security rating for a single open-source project.
 */
class SingleSecurityRatingCalculator extends AbstractRatingCalculator {

  /**
   * Initializes a new calculator.
   *
   * @param fetcher An interface to GitHub.
   * @param nvd An interface to NVD.
   */
  SingleSecurityRatingCalculator(GitHubDataFetcher fetcher, NVD nvd) {
    super(fetcher, nvd);
  }

  @Override
  public SingleSecurityRatingCalculator calculateFor(GitHubProject project) throws IOException {
    Objects.requireNonNull(project, "Oh no! Project can't be null!");

    logger.info("Let's gather info and calculate a security rating for:");
    logger.info("  {}", project.scm());

    try {
      fetcher.repositoryFor(project);
    } catch (IOException e) {
      logger.error("Looks like something is wrong with the project!", e);
      logger.warn("Let's skip the project ...");
      return this;
    }

    OssSecurityRating rating = RatingRepository.INSTANCE.rating(OssSecurityRating.class);

    ValueSet values = ValueHashSet.unknown(rating.allFeatures());
    for (DataProvider<GitHubProject> provider : dataProviders()) {

      // skip data providers that talk to users but the callback doesn't allow that
      if (provider.interactive() && !callback.canTalk()) {
        continue;
      }

      try {
        provider.set(callback).set(cache).update(project, values);
      } catch (Exception e) {
        logger.warn("Holy Moly, {} data provider failed!",
            provider.getClass().getSimpleName());
        logger.warn("The last thing that it said was", e);
        logger.warn("But we don't give up!");
      }
    }

    logger.info("Here is what we know about the project:");
    Arrays.stream(values.toArray())
        .sorted(Comparator.comparing(value -> value.feature().name()))
        .forEach(value -> logger.info("   {}: {}", value.feature(), value));

    project.set(rating.calculate(values));

    return this;
  }

  @Override
  public SingleSecurityRatingCalculator calculateFor(List<GitHubProject> projects) {
    throw new UnsupportedOperationException("I can't handle multiple projects!");
  }

  /**
   * Initializes a list of data providers that are going to be used by the calculator.
   *
   * @return The list of data providers.
   * @throws IOException If something went wrong during the initialization.
   */
  List<DataProvider<GitHubProject>> dataProviders() throws IOException {
    return Arrays.asList(
        new NumberOfCommits(fetcher),
        new NumberOfContributors(fetcher),
        new NumberOfStars(fetcher),
        new NumberOfWatchers(fetcher),
        new HasSecurityTeam(fetcher),
        new HasCompanySupport(fetcher),
        new HasSecurityPolicy(fetcher),
        new HasBugBountyProgram(fetcher),
        new InfoAboutVulnerabilities(fetcher, nvd),
        new IsApache(fetcher),
        new IsEclipse(fetcher),
        new CodeqlDataProvider(fetcher),
        new LgtmDataProvider(fetcher),
        new UsesSignedCommits(fetcher),
        new UsesDependabot(fetcher),
        new ProgrammingLanguages(fetcher),
        new PackageManagement(fetcher),
        new UsesNoHttpTool(fetcher),
        new UsesGithubForDevelopment(fetcher),
        new UsesOwaspDependencyCheck(fetcher),
        new UsesSanitizers(fetcher),
        new UsesFindSecBugs(fetcher),
        new FuzzedInOssFuzz(fetcher),
        new SignsJarArtifacts(fetcher),
        new OwaspSecurityLibraries(fetcher),

        // currently interactive data provider have to be added to the end, see issue #133
        new AskAboutSecurityTeam<>(),
        new AskAboutUnpatchedVulnerabilities<>()
    );
  }
}
