/**
 * FastaFileFilter.java 
 * Copyright (C) 2016 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package jloda.util;

import java.io.File;
import java.io.FilenameFilter;

/**
 * The fastA file filter
 * Daniel Huson         2.2006
 */
public class FastaFileFilter extends FileFilterBase implements FilenameFilter {
    private static FastaFileFilter instance;

    /**
     * gets an instance
     *
     * @return instance
     */
    public static FastaFileFilter getInstance() {
        if (instance == null) {
            instance = new FastaFileFilter();
            instance.setAllowGZipped(true);
            instance.setAllowZipped(true);
        }
        return instance;
    }

    /**
     * constructor
     */
    public FastaFileFilter() {
        add(".dna");
        add(".fa");
        add(".faa");
        add(".fna");
        add(".fasta");
    }

    public boolean accept (String fileName) {
        boolean acceptBasedOnSuffix=super.accept(fileName);
        if(!acceptBasedOnSuffix && isAllowGZipped() && fileName.toLowerCase().endsWith(".gz")) {   // look inside a gzipped file
            final String[] lines=Basic.getFirstLinesFromFile(new File(fileName),2);
            return lines!=null && lines[0]!=null && isFastAHeaderLine(lines[0])   && lines[1]!=null && !lines[1].startsWith(">");
        }
        else
            return true;
    }

    /**
     * @return description of file matching the filter
     */
    public String getBriefDescription() {
        return "Sequence files in FastA format";
    }

    /**
     * does this look like a FastA file name?
     *
     * @param fileName
     * @return true, if fastA file name
     */
    public static boolean accept(String fileName, boolean allowGZipped) {
        final FastaFileFilter fastaFileFilter = (new FastaFileFilter());
        fastaFileFilter.setAllowGZipped(allowGZipped);
        return fastaFileFilter.accept(fileName);
    }

    private static boolean isFastAHeaderLine(String line) {
        if(line.startsWith("> ")) {
            return line.length()>2 && Character.isLetterOrDigit(line.charAt(2));
        } else  if(line.startsWith(">")) {
            return line.length()>1 && Character.isLetterOrDigit(line.charAt(1));
        }
        else
            return false;
    }
}
