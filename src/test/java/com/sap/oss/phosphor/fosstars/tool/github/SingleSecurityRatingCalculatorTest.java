package com.sap.oss.phosphor.fosstars.tool.github;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.sap.oss.phosphor.fosstars.data.NoUserCallback;
import com.sap.oss.phosphor.fosstars.data.github.IsApache;
import com.sap.oss.phosphor.fosstars.data.github.IsEclipse;
import com.sap.oss.phosphor.fosstars.data.github.TestGitHubDataFetcherHolder;
import com.sap.oss.phosphor.fosstars.model.math.DoubleInterval;
import com.sap.oss.phosphor.fosstars.model.rating.oss.OssSecurityRating.SecurityLabel;
import com.sap.oss.phosphor.fosstars.model.subject.oss.GitHubProject;
import com.sap.oss.phosphor.fosstars.model.value.RatingValue;
import com.sap.oss.phosphor.fosstars.nvd.NVD;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;
import org.kohsuke.github.GHRepository;

public class SingleSecurityRatingCalculatorTest extends TestGitHubDataFetcherHolder {

  @Test
  public void testCalculateFor() throws IOException {
    GHRepository repository = mock(GHRepository.class);
    when(fetcher.github().getRepository(any())).thenReturn(repository);

    SingleSecurityRatingCalculator calculator
        = new SingleSecurityRatingCalculator(fetcher, new NVD());

    calculator.token("test");
    calculator.set(NoUserCallback.INSTANCE);
    calculator = spy(calculator);

    when(calculator.dataProviders())
        .thenReturn(Arrays.asList(new IsApache(fetcher), new IsEclipse(fetcher)));

    GitHubProject apacheNiFi = new GitHubProject("apache", "nifi");
    assertFalse(apacheNiFi.ratingValue().isPresent());

    calculator.calculateFor(apacheNiFi);

    assertTrue(apacheNiFi.ratingValue().isPresent());
    RatingValue ratingValue = apacheNiFi.ratingValue().get();
    assertEquals(SecurityLabel.UNCLEAR, ratingValue.label());
    assertTrue(DoubleInterval.closed(0, 3).contains(ratingValue.scoreValue().confidence()));
    assertTrue(DoubleInterval.closed(0, 3).contains(ratingValue.scoreValue().get()));
  }

  @Test
  public void testCalculateForWithNoContent() throws IOException {
    GHRepository repository = mock(GHRepository.class);
    when(fetcher.github().getRepository(any())).thenReturn(repository);
    when(repository.getDirectoryContent(any())).thenThrow(new IOException());
    checkNoRating();
  }

  @Test
  public void testCalculateForWithNullRepository() throws IOException {
    when(fetcher.github().getRepository(any())).thenReturn(null);
    checkNoRating();
  }

  @Test
  public void testCalculateForWithExceptionWhenFetchingRepository() throws IOException {
    when(fetcher.github().getRepository(any())).thenThrow(new IOException());
    checkNoRating();
  }

  private void checkNoRating() throws IOException {
    SingleSecurityRatingCalculator calculator
        = new SingleSecurityRatingCalculator(fetcher, new NVD());

    calculator.token("test");
    calculator.set(NoUserCallback.INSTANCE);
    calculator = spy(calculator);

    when(calculator.dataProviders())
        .thenReturn(Arrays.asList(new IsApache(fetcher), new IsEclipse(fetcher)));

    GitHubProject apacheNiFi = new GitHubProject("apache", "nifi");
    assertFalse(apacheNiFi.ratingValue().isPresent());

    calculator.calculateFor(apacheNiFi);
    assertFalse(apacheNiFi.ratingValue().isPresent());
  }
}