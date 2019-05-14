/**
   Copyright (c) 2007, University of Pittsburgh

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

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;

import org.eclipse.rdf4j.query.QueryInterruptedException;
import pitt.search.semanticvectors.utils.VerbatimLogger;

import java.io.*;
import java.nio.file.FileSystems;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class provides methods for serializing a VectorStore to disk.
 * 
 * <p>
 * The serialization currently presumes that the object (in the ObjectVectors)
 * should be serialized as a String.
 * 
 * <p>
 * The implementation uses Lucene's I/O package, which proved much faster
 * than the native java.io.DataOutputStream.
 * 
 * @see ObjectVector
 */
public class VectorStoreWriter {

  /**
   * Generates a single string containing global header information for a vector store.
   * This includes the vector type and the dimension.
   * 
   * String created is in the form that {@code Flags} would expect to parse, e.g.,
   * "-vectortype real -dimension 100".
   */
  public static String generateHeaderString(FlagConfig flagConfig) {
    return "-vectortype " + flagConfig.vectortype().toString()
        + " -dimension " + Integer.toString(flagConfig.dimension());
  }

  public static void writeVectors(String storeName, FlagConfig flagConfig, VectorStore objectVectors) throws IOException {
    writeVectors(storeName, flagConfig, objectVectors, new AtomicBoolean(false));
  }

  /**
   * Writes vectors in text or lucene format depending on {@link FlagConfig#indexfileformat}.
   * 
   * @param storeName The name of the vector store to write to
   * @param objectVectors The vector store to be written to disk
   */
  public static void writeVectors(String storeName, FlagConfig flagConfig, VectorStore objectVectors, AtomicBoolean isCreationInterruptedByUser)
      throws IOException {
    String vectorFileName = VectorStoreUtils.getStoreFileName(storeName, flagConfig);
    switch (flagConfig.indexfileformat()) {
    case LUCENE:
      writeVectorsInLuceneFormat(vectorFileName, flagConfig, objectVectors, isCreationInterruptedByUser);
      break;
    case TEXT:
      writeVectorsInTextFormat(vectorFileName, flagConfig, objectVectors, isCreationInterruptedByUser);
      break;
    default:
      throw new IllegalStateException("Unknown -indexfileformat: " + flagConfig.indexfileformat());
    }
  }

  public static void writeVectorsInLuceneFormat(String vectorFileName, FlagConfig flagConfig, VectorStore objectVectors) throws IOException {
    writeVectorsInLuceneFormat(vectorFileName, flagConfig, objectVectors, new AtomicBoolean(false));
  }

  /**
   * Outputs a vector store in Lucene binary format.
   * 
   * @param vectorFileName The name of the file to write to
   * @param objectVectors The vector store to be written to disk
   */
  public static void writeVectorsInLuceneFormat(String vectorFileName, FlagConfig flagConfig, VectorStore objectVectors, AtomicBoolean isCreationInterruptedByUser)
      throws IOException {
    VerbatimLogger.info("About to write " + objectVectors.getNumVectors() + " vectors of dimension "
        + flagConfig.dimension() + " to Lucene format file: " + vectorFileName + " ... ");
    File vectorFile = new File(vectorFileName);
    java.nio.file.Files.deleteIfExists(vectorFile.toPath());
    String parentPath = vectorFile.getParent();
    if (parentPath == null) parentPath = "";
    FSDirectory fsDirectory = FSDirectory.open(FileSystems.getDefault().getPath(parentPath));
    IndexOutput outputStream = fsDirectory.createOutput(vectorFile.getName(), IOContext.DEFAULT);

    // This map exploits the fact that the keys are longs from the entity pool
    TreeMap<String, Long> entityMap = new TreeMap<>();
    writeToIndexOutput(objectVectors, flagConfig, outputStream, entityMap, isCreationInterruptedByUser);
    writeEntityMap(entityMap, new File(vectorFile.getAbsolutePath() + ".map"));

    outputStream.close();
    fsDirectory.close();
  }

  private static void writeEntityMap(TreeMap<String, Long> entityMap, File file) {
    try (DataOutputStream os = new DataOutputStream(new FileOutputStream(file))) {
      for (Long value : entityMap.values()) {
        os.writeLong(value);
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void writeToIndexOutput(VectorStore objectVectors, FlagConfig flagConfig, IndexOutput outputStream,
                                        Map<String, Long> entityMap) throws IOException {
    writeToIndexOutput(objectVectors, flagConfig, outputStream, entityMap, new AtomicBoolean(false));
  }

  /**
   * Writes the object vectors to this Lucene output stream.
   * Caller is responsible for opening and closing stream output stream.
   */
  public static void writeToIndexOutput(VectorStore objectVectors, FlagConfig flagConfig, IndexOutput outputStream,
                                        Map<String, Long> entityMap, AtomicBoolean isCreationInterruptedByUser)
      throws IOException {
    // Write header giving vector type and dimension for all vectors.
    outputStream.writeString(generateHeaderString(flagConfig));
    Enumeration<ObjectVector> vecEnum = objectVectors.getAllVectors();

    // Write each vector.
    while (vecEnum.hasMoreElements()) {
      ObjectVector objectVector = vecEnum.nextElement();

      if(entityMap != null)
        entityMap.put(objectVector.getObject().toString(), outputStream.getFilePointer());

      outputStream.writeString(objectVector.getObject().toString());
      objectVector.getVector().writeToLuceneStream(outputStream, isCreationInterruptedByUser);
    }
    VerbatimLogger.info("finished writing vectors.\n");
  }

  public static void writeVectorsInTextFormat(String vectorFileName, FlagConfig flagConfig,
                                              VectorStore objectVectors) throws IOException {
    writeVectorsInTextFormat(vectorFileName, flagConfig, objectVectors, new AtomicBoolean(false));
  }

  /**
   * Outputs a vector store as a plain text file.
   * 
   * @param vectorFileName The name of the file to write to
   * @param flagConfig For reading dimension and vector type
   * @param objectVectors The vector store to be written to disk
   */
  public static void writeVectorsInTextFormat(String vectorFileName, FlagConfig flagConfig,
                                              VectorStore objectVectors, AtomicBoolean isCreationInterruptedByUser)
      throws IOException {
    VerbatimLogger.info("About to write " + objectVectors.getNumVectors() + " vectors of dimension "
        + flagConfig.dimension() + " to text file: " + vectorFileName + " ... ");
    BufferedWriter outBuf = new BufferedWriter(new FileWriter(vectorFileName));
    writeToTextBuffer(objectVectors, flagConfig, outBuf, isCreationInterruptedByUser);
    outBuf.close();
    VerbatimLogger.info("finished writing vectors.\n");
  }

  public static void writeToTextBuffer(VectorStore objectVectors, FlagConfig flagConfig,
                                       BufferedWriter outBuf) throws IOException {
    writeToTextBuffer(objectVectors, flagConfig, outBuf, new AtomicBoolean(false));
  }

  public static void writeToTextBuffer(VectorStore objectVectors, FlagConfig flagConfig,
                                       BufferedWriter outBuf, AtomicBoolean isCreationInterruptedByUser)
      throws IOException {
    Enumeration<ObjectVector> vecEnum = objectVectors.getAllVectors();

    // Write header giving vector type and dimension for all vectors.
    outBuf.write(generateHeaderString(flagConfig) + "\n");

    // Write each vector.
    while (vecEnum.hasMoreElements()) {
      if (isCreationInterruptedByUser.get()) {
        throw new QueryInterruptedException("Transaction was aborted by the user");
      }
      ObjectVector objectVector = vecEnum.nextElement();
      outBuf.write(objectVector.getObject().toString().replaceAll("\\|", ";") + "|");
      outBuf.write(objectVector.getVector().writeToString());
      outBuf.write("\n");
    }    
  }
}
