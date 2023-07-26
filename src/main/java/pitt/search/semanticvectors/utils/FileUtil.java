package pitt.search.semanticvectors.utils;

import java.io.File;

/**
 * Utility for working with {@link File}s.
 *
 * @author <a href="mailto:borislav.bonev@ontotext.com">Borislav Bonev</a>
 * @since 26/07/2023
 */
public class FileUtil {

  private static final long HALF_GB = 512L * 1024 * 1024;
  private static final long FIVE_GB = 5L * 1024 * 1024 * 1024;

  /**
   * Check the available disk space and fail with exception id the free space is less than half gigabyte.
   *
   * @param location the location to check.
   */
  public static void checkDiskSpace(File location) {
    long freeSpace = Math.min(location.getUsableSpace(), location.getFreeSpace());
    if (HALF_GB > freeSpace) {
      throw new NoDiskSpaceException("Insufficient disk space at " + location);
    }
    if (FIVE_GB > freeSpace) {
      VerbatimLogger.warning("The location " + location + " is running ouf of disk space with remaining " + freeSpace + " bytes");
    }
  }

}
