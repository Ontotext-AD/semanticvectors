package pitt.search.lucene;
import java.io.File;
import java.io.FileReader;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableField;

/**
 *  This class makes a minor modification to org.apache.lucene.FileDocument
 *  such that it records TermPositionVectors for each document
 *  @author Trevor Cohen
 */
public class FilePositionDoc  {

//takes a file as input
  public static Document Document(File f)
       throws java.io.FileNotFoundException {
    Document doc = new Document();
    doc.add(new StoredField("path", f.getPath()));
    doc.add(new StoredField("modified",
                      DateTools.timeToString(f.lastModified(), DateTools.Resolution.MINUTE)));
    
    //create new FieldType to store term positions (TextField is not sufficiently configurable)
    FieldType ft = new FieldType();
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    ft.setTokenized(true);
    ft.setStoreTermVectors(true);
    ft.setStoreTermVectorPositions(true);
    Field contentsField = new Field("contents", new FileReader(f), ft);

    doc.add(contentsField);
    return doc;
  }

 
//takes a String as input, as well as an int to be the document title
  public static Document Document(String inLine, int lineNumber) {
	  	
		Document doc = new Document();
	    doc.add(new StoredField("line_number", ""+lineNumber));
	    doc.add(new StoredField("modified",
	                      DateTools.timeToString(System.currentTimeMillis(), DateTools.Resolution.MINUTE)));
	    
	    //create new FieldType to store term positions (TextField is not sufficiently configurable)
	    FieldType ft = new FieldType();
	    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
	    ft.setTokenized(true);
	    ft.setStoreTermVectors(true);
	    ft.setStoreTermVectorPositions(true);
	    Field contentsField = new Field("contents", inLine, ft);

	    doc.add(contentsField);
	    return doc;
}
}

