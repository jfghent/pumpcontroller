/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iotest2;

import java.io.*;
/**
 *
 * @author Jon
 */
public class SimplestFileReader {
    

    public String get(String path) throws FileNotFoundException, IOException {
        char[] cbuf = new char[32];
        
        FileReader fr = new FileReader(path);
        fr.read(cbuf, 0, 32);
        return new String(cbuf).trim();
    }
  
}
/********
 * java.​io.​InputStreamReader
 * public int read(char[] cbuf, int offset, int length) throws IOException
 * Reads characters into a portion of an array.
 * 
 * Parameters:
 *   cbuf - Destination buffer 
 *   offset - Offset at which to start storing characters 
 *   length - Maximum number of characters to read 
 * Returns:
 *   The number of characters read, or -1 if the end of the stream has been reached 
 * Throws:
 *   IOException - If an I/O error occurs
 *********/