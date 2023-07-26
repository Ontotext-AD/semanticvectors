package pitt.search.semanticvectors.utils;

import java.io.File;

/**
 * Utility for working with {@link File}s.
 *
 * @author <a href="mailto:borislav.bonev@ontotext.com">Borislav Bonev</a>
 * @since 26/07/2023
 */
public class FileUtil {

  private static final long HALF_GB = 512 * 1024 * 1024;

  /**
   * Check the available disk space and fail with exception id the free space is less than half gigabyte.
   *
   * @param location the location to check.
   */
  public static void checkDiskSpace(File location) {
    long usableSpace = location.getUsableSpace();
    long freeSpace = location.getFreeSpace();
    if (HALF_GB > usableSpace || HALF_GB > freeSpace) {
      throw new NoDiskSpaceException("Insufficient disk space at " + location);
    }
  }

}
