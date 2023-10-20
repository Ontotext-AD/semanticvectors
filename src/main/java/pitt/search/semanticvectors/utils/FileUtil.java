package pitt.search.semanticvectors.utils;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;

import java.io.File;

/**
 * Utility for working with {@link File}s.
 *
 * @author <a href="mailto:borislav.bonev@ontotext.com">Borislav Bonev</a>
 * @since 26/07/2023
 */
public class FileUtil {

  static final String SEMANTIC_VECTORS_MINIMAL_FREE_DISK_SPACE = "semantic-vectors.minimal-free-disk-space";
  private static long hardLimit = -1;
  private static final long FIVE_GB = 5L * 1024 * 1024 * 1024;

  /**
   * Check the available disk space and fail with exception id the free space is less than half gigabyte.
   *
   * @param location the location to check.
   */
  public static void checkDiskSpace(File location) {
    long freeSpace = Math.min(location.getUsableSpace(), location.getFreeSpace());
    if (resolveHardLimit() > freeSpace) {
      throw new NoDiskSpaceException("Insufficient disk space at " + location);
    }
    if (FIVE_GB > freeSpace) {
      VerbatimLogger.warning("The location " + location + " is running ouf of disk space with remaining " + freeSpace + " bytes");
    }
  }

  @VisibleForTesting
  static long resolveHardLimit() {
    if (hardLimit == -1) {
      String value = System.getProperty(SEMANTIC_VECTORS_MINIMAL_FREE_DISK_SPACE);
      long limit = 512L * 1024 * 1024; // 500 MB
      if (StringUtils.isNotBlank(value)) {
        try {
          long multiplier = 1;
          if (value.toLowerCase().endsWith("gb")) {
            value = value.substring(0, value.length() - 2);
            multiplier = 1024 * 1024 * 1024;
          }
          if (value.toLowerCase().endsWith("mb")) {
            value = value.substring(0, value.length() - 2);
            multiplier = 1024 * 1024;
          }
          limit = (long) (Double.parseDouble(value.replace(',', '.').trim()) * multiplier);
        } catch (NumberFormatException nfe) {
          VerbatimLogger.warning(
                  "Invalid configuration value 'semantic-vectors.minimal-free-disk-space'. Supported formats: 0.5GB, 500MB, " +
                          512L * 1024 * 1024);
        }
      }
      hardLimit = limit;
    }
    return hardLimit;
  }

  @VisibleForTesting
  static void resetLimit() {
    hardLimit = -1;
  }
}
