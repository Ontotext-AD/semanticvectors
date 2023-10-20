package pitt.search.semanticvectors.utils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test for {@link FileUtil}.
 *
 * @author <a href="mailto:borislav.bonev@ontotext.com">Borislav Bonev</a>
 * @since 20/10/2023
 */
public class FileUtilTest {

  @After
  public void tearDown() {
    System.clearProperty(FileUtil.SEMANTIC_VECTORS_MINIMAL_FREE_DISK_SPACE);
    FileUtil.resetLimit();
  }

  @Test
  public void resolveHardLimit() {
    setLimit("100gb");
    Assert.assertEquals(100L * 1024 * 1024 * 1024, FileUtil.resolveHardLimit());
    FileUtil.resetLimit();

    setLimit("10.5gb");
    Assert.assertEquals(10.5 * 1024 * 1024 * 1024, FileUtil.resolveHardLimit(), 1);
    FileUtil.resetLimit();

    setLimit("100MB");
    Assert.assertEquals(100L * 1024 * 1024, FileUtil.resolveHardLimit());
    FileUtil.resetLimit();

    setLimit("10.5mb");
    Assert.assertEquals(10.5 * 1024 * 1024, FileUtil.resolveHardLimit(), 1);
    FileUtil.resetLimit();
  }

  private void setLimit(String limit) {
    System.setProperty(FileUtil.SEMANTIC_VECTORS_MINIMAL_FREE_DISK_SPACE, limit);
  }
}