/**
   Copyright (c) 2007, University of Pittsburgh
   Copyright (c) 2008 and ongoing, the SemanticVectors AUTHORS.

   All rights reserved.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are
   met:

 * Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the following
   disclaimer in the documentation and/or other materials provided
   with the distribution.

 * Neither the name of the University of Pittsburgh nor the names
   of its contributors may be used to endorse or promote products
   derived from this software without specific prior written
   permission.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
   CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
   EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
   PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
   LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
   NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **/

package pitt.search.semanticvectors;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import pitt.search.semanticvectors.lsh.DirectByteBufferCleaner;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystems;
import java.util.Enumeration;
import java.util.logging.Logger;

/**
   This class provides methods for reading a VectorStore from disk. <p>

   The serialization currently presumes that the object (in the ObjectVectors)
   should be serialized as a String. <p>

   The implementation uses Lucene's I/O package, which proved much faster
   than the native java.io.DataOutputStream.
   
   Attempts to be thread-safe but this is not fully tested.
   
   @see ObjectVector
 **/
public class VectorStoreReaderLucene implements CloseableVectorStore {
  private static final Logger logger = Logger.getLogger(
      VectorStoreReaderLucene.class.getCanonicalName());

  private String vectorFileName;
  private File vectorFile;
  private Directory directory;
  private FlagConfig flagConfig;
  
  private ThreadLocal<IndexInput> threadLocalIndexInput;

  public IndexInput getIndexInput() {
    return threadLocalIndexInput.get();
  }
  
  public VectorStoreReaderLucene(String vectorFileName, FlagConfig flagConfig) throws IOException {
    this.flagConfig = flagConfig;
    this.vectorFileName = vectorFileName;
    this.vectorFile = new File(vectorFileName);
    try {
      String parentPath = this.vectorFile.getParent();
      if (parentPath == null) parentPath = "";
      this.directory = FSDirectory.open(FileSystems.getDefault().getPath(parentPath));  // Old from FSDirectory impl.
      // Read number of dimension from header information.
      this.threadLocalIndexInput = new ThreadLocal<IndexInput>() {
        @Override
        protected IndexInput initialValue() {
          try {
            return directory.openInput(vectorFile.getName(), IOContext.READ);
          } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
          }
        }
      };
      readHeadersFromIndexInput(flagConfig);
    } catch (IOException e) {
      logger.warning("Cannot open file: " + this.vectorFileName + "\n" + e.getMessage());
      throw e;
    }
  }
  
  /**
   * Only for testing!  This does not create an FSDirectory so calling "close()" gives NPE.
   * TODO(widdows): Fix this, and ownership of FSDirectory or RAMDirectory!
   */
  protected VectorStoreReaderLucene(ThreadLocal<IndexInput> threadLocalIndexInput, FlagConfig flagConfig)
      throws IOException {
    this.threadLocalIndexInput = threadLocalIndexInput;
    this.flagConfig = flagConfig;
    readHeadersFromIndexInput(flagConfig);
  }

  /**
   * Sets internal dimension and vector type, and flags in flagConfig to match.
   * 
   * @throws IOException
   */
  public void readHeadersFromIndexInput(FlagConfig flagConfig) throws IOException {
    String header = threadLocalIndexInput.get().readString();
    FlagConfig.mergeWriteableFlagsFromString(header, flagConfig);
  }

  public void close() {
    this.closeIndexInput();
    try {
      this.directory.close();
    } catch (IOException e) {
      logger.severe("Failed to close() directory resources: have they already been destroyed?");
      e.printStackTrace();
    }
  }

  public void closeIndexInput() {
    try {
      this.getIndexInput().close();
    } catch (IOException e) {
      logger.info("Cannot close resources from file: " + this.vectorFile
          + "\n" + e.getMessage());
    }
  }

  public Enumeration<ObjectVector> getAllVectors() {
    try {
      getIndexInput().seek(0);
      // Skip header line.
      getIndexInput().readString();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return new VectorEnumeration(getIndexInput());
  }

  /**
   * Given an object, get its corresponding vector <br>
   * This implementation only works for string objects so far <br>
   * 
   * @param desiredObject - the string you're searching for
   * @return vector from the VectorStore, or null if not found.
   */
  public Vector getVector(Object desiredObject) {
    File map;
    if ((map = new File(vectorFileName + ".map")).exists()) {
      Vector vector = findVectorInMap(map, desiredObject);

      if (vector == null) {
        logger.info("Didn't find vector for '" + desiredObject + "'\n");
      }
      return vector;
    }
    try {
      String stringTarget = desiredObject.toString();
      getIndexInput().seek(0);
      // Skip header line.
      getIndexInput().readString();
      while (getIndexInput().getFilePointer() < getIndexInput().length() - 1) {
        String objectString = getIndexInput().readString();
        if (objectString.equals(stringTarget)) {
          VerbatimLogger.info("Found vector for '" + stringTarget + "'\n");
          Vector vector = VectorFactory.createZeroVector(
              flagConfig.vectortype(), flagConfig.dimension());
          vector.readFromLuceneStream(getIndexInput());
          return vector;
        }
        else{
          getIndexInput().seek(getIndexInput().getFilePointer()
              + VectorFactory.getLuceneByteSize(flagConfig.vectortype(), flagConfig.dimension()));
        }
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    VerbatimLogger.info("Didn't find vector for '" + desiredObject + "'\n");
    return null;
  }

  private Vector findVectorInMap(File map, Object desiredObject) {
    try (FileChannel channel = FileChannel.open(map.toPath())) {
      return findVectorInMap(channel, desiredObject, 0, (int) channel.size() / Long.BYTES);
    } catch (IOException e) {
      logger.severe(e.getMessage());
      return null;
    }
  }

  private Vector findVectorInMap(FileChannel map, Object desiredObject, int start, int end) {
    if (end < start)
      return null;

    ByteBuffer buffer = ByteBuffer.allocateDirect(Long.BYTES);
    int testIdx = (start + end) / 2;
    try {
      // Secondary bottom of the recursion, if channel has reached end of stream
      if (map.position(testIdx * Long.BYTES).read(buffer) == -1) {
        return null;
      }
      buffer.flip();
      getIndexInput().seek(buffer.getLong());

      String testObject = getIndexInput().readString();
      if (testObject.equals(desiredObject.toString())) {
        VerbatimLogger.info("Found vector for '" + desiredObject + "'\n");
        Vector vector = VectorFactory.createZeroVector(
                flagConfig.vectortype(), flagConfig.dimension());
        vector.readFromLuceneStream(getIndexInput());
        return vector;
      } else if (testObject.compareTo(desiredObject.toString()) > 0) {
        return findVectorInMap(map, desiredObject, start, testIdx - 1);
      } else {
        return findVectorInMap(map, desiredObject, testIdx + 1, end);
      }
    } catch (IOException e) {
      logger.severe(e.getMessage());
    } finally {
      DirectByteBufferCleaner.closeDirectByteBuffer(buffer);
    }
    return null;
  }

  /**
   * Trivial (costly) implementation of getNumVectors that iterates and counts vectors.
   */
  public int getNumVectors() {
    Enumeration<ObjectVector> allVectors = this.getAllVectors();
    int i = 0;
    while (allVectors.hasMoreElements()) {
      allVectors.nextElement();
      ++i;
    }
    return i;
  }
  
  /**
   * Implements the hasMoreElements() and nextElement() methods
   * to give Enumeration interface from store on disk.
   */
  public class VectorEnumeration implements Enumeration<ObjectVector> {
    IndexInput indexInput;

    public VectorEnumeration(IndexInput indexInput) {
      this.indexInput = indexInput;
    }

    public boolean hasMoreElements() {
      return (indexInput.getFilePointer() < indexInput.length());
    }

    public ObjectVector nextElement() {
      String object = null;
      Vector vector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
      try {
        object = indexInput.readString();
        vector.readFromLuceneStream(indexInput);
      }
      catch (IOException e) {
        e.printStackTrace();
      }
      return new ObjectVector(object, vector);
    }
  }
  
  @Override
  public boolean containsVector(Object object) {
	  return this.getVector(object) != null;
  }

  public final File getVectorFile() {
    return vectorFile;
  }

}
