package uk.ac.tgac.nanook.core;

import net.sourceforge.fluxion.spi.Spi;

import java.util.List;

/**
 * Interface to describe parsers that can parse an alignment files.
 * 
 * @author Richard Leggett / Robert Davey
 */

@Spi
public interface AlignmentParserInterface {
    /**
     * Parse an alignment file.
     *
     * @param filename the filename of the alignments file
     * @param summaryFile the name of an alignments table summary file to write
     * @return 
     */
    int parseFile(String filename, AlignmentsTableFile summaryFile);

    /**
     * Sort alignments by score
     */
    void sortAlignments();

    /**
     * Get highest scoring set of alignments (ie. highest scoring reference)
     * @return an ArrayList of Alignment objects
     */
    List<Alignment> getHighestScoringSet();
}
