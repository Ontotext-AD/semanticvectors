package pitt.search.semanticvectors.utils;

/**
 * Exception thrown when insufficient disk space is detected for particular indexing operation.
 *
 * @author <a href="mailto:borislav.bonev@ontotext.com">Borislav Bonev</a>
 * @since 26/07/2023
 */
public class NoDiskSpaceException extends RuntimeException {

  public NoDiskSpaceException(String message) {
    super(message);
  }
}
