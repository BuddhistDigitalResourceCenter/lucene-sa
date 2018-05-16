package io.bdrc.lucene.sa;

import java.io.BufferedReader;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import io.bdrc.lucene.stemmer.Reduce;
import io.bdrc.lucene.stemmer.Trie;

public class BuildCompiledTrie {
	/**
	 * Builds a Trie from all the entries in a list of files
	 * Dumps it in a binary file
	 * 
	 * !!! Ensure to have enough Stack memory 
	 * ( -Xss40m seems to be enough for all the inflected forms of Sanskrit Heritage)
	 * 
	 */
	
	static boolean optimize = false;	// change to true to optimize the Trie
	static String outFile = "src/main/resources/skrt-compiled-trie.dump";
	public static List<String> inputFiles = Arrays.asList(
			"resources/sanskrit-stemming-data/output/trie_content.txt"	// all Sanskrit Heritage entries + custom entries
			);
	
	public static void main(String [] args){
		
		try {
			Trie trie = buildTrie(inputFiles);
			
			if (optimize) {
				trie = optimizeTrie(trie, new Reduce());		
				storeTrie(trie, "src/main/resources/skrt-compiled-trie_optimized.dump");	
			} else {
				storeTrie(trie, outFile);
			}
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * used in {@link SkrtWordTokenizer} constructors
	 * 
	 * builds the Trie, then stores it to a file (no optimization)
	 * 
	 * @throws FileNotFoundException  input or output file not found
	 * @throws IOException  input can't be read or output can't be written
	 */
	public static void compileTrie() throws FileNotFoundException, IOException {
		Trie trie = buildTrie(inputFiles);
		storeTrie(trie, outFile);
	}
	
	/** 
	 * 
	 * @param inputFiles  the list of files to feed the Trie with
	 * @return the non-optimized Trie
	 * @throws FileNotFoundException  input file not found
	 * @throws IOException  output file can't be written
	 */
	public static Trie buildTrie(List<String> inputFiles) throws FileNotFoundException, IOException {
		System.out.println("\tBuilding the Trie from the raw text file…");
	    long one = System.currentTimeMillis();
		/* Fill the Trie with the content of all inputFiles*/
		Trie trie = new Trie(true);
		for (String filename: inputFiles) {
			try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
				String line;
				while ((line = br.readLine()) != null) {
					final int sepIndex = line.indexOf(',');
					if (sepIndex == -1) {
						throw new IllegalArgumentException("The dictionary file is corrupted in the following line.\n" + line);
					} else {
						trie.add(line.substring(0, sepIndex), line.substring(sepIndex+1));
					}
				}
			}
		}
		long two = System.currentTimeMillis();
		System.out.println("\tTime: " + (two - one) / 1000 + "s.");
		return trie;
	}
	
	/**
	 *  
	 * Optimizer  - optimization time: 10mn ; compiled Trie size: 10mo
	 * Optimizer2 - optimization time: 12mn ; compiled Trie size: 12mo
	 * 
	 * @param trie trie to be optimized
	 * @param optimizer  optimizer to be used
	 * @return  the optimized trie
	 */
	public static Trie optimizeTrie(Trie trie, Reduce optimizer) {
		long three = System.currentTimeMillis();
		trie = optimizer.optimize(trie);
		long four = System.currentTimeMillis();
		System.out.println("Optimizing the Trie took: " + (four - three) / 1000 + "s.");
		return trie;
	}
	
	/**
	 * 
	 * @param trie  the trie to store in binary format
	 * @param outFilename  the path+filename of the output file
     * @throws FileNotFoundException  output file not found
     * @throws IOException  output file can't be written
	 */
	public static void storeTrie(Trie trie, String outFilename) throws FileNotFoundException, IOException {
		OutputStream output = new DataOutputStream(new FileOutputStream(outFilename));
		trie.store((DataOutput) output);
	}	
}
