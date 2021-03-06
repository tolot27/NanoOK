/*
 * Program: NanoOK
 * Author:  Richard M. Leggett
 * 
 * Copyright 2015 The Genome Analysis Centre (TGAC)
 */

package nanook;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Entry class for tool.
 * 
 * @author Richard Leggett
 */
public class NanoOK {
    public final static String VERSION_STRING = "v0.54";
    public final static long SERIAL_VERSION = 3L;
    
    /**
     * Check for program dependencies - R, pdflatex
     */
    public static void checkDependencies() {
        ProcessLogger pl = new ProcessLogger();
        ArrayList<String> response;
        String rVersion = null;
        String pdflatexVersion = null;
        String hVersion = null;
                
        response = pl.checkCommand("Rscript --version");
        if (response != null) {
            for (int i=0; i<response.size(); i++) {
                String s = response.get(i);
                if (s.startsWith("R scripting front-end")) {
                    rVersion = s;
                }
            }
        }
        
        if (rVersion == null) {
            System.out.println("*** ERROR: Couldn't find Rscript - is R installed? ***");
        } else {
            System.out.println(rVersion);
        }
        
        response = pl.checkCommand("pdflatex --version");
        if (response != null) {
            for (int i=0; i<response.size(); i++) {
                String s = response.get(i);
                if (s.contains("pdfTeX")) {
                    pdflatexVersion = s;
                    break;
                }
            }
        }
        
        if (pdflatexVersion == null) {
            System.out.println("*** ERROR: Couldn't find pdflatex - is TeX installed? ***");
        } else {
            System.out.println(pdflatexVersion);
        }

        response = pl.checkCommand("h5dump --version");
        if (response != null) {
            for (int i=0; i<response.size(); i++) {
                String s = response.get(i);
                if (s.startsWith("h5dump")) {
                    hVersion = s;
                }
            }
        }
        
        if (hVersion == null) {
            System.out.println("*** ERROR: Couldn't find h5dump - is H5 Tools installed? ***");
        } else {
            System.out.println(hVersion);
        }
        
        System.out.println("");
    }

    /**
     * Test logo plotting
     */
    public static void testLogo() {
        SequenceLogo logo = new SequenceLogo();
        logo.drawImage();
        logo.saveImage("/Users/leggettr/Desktop/logo.png");
    }
    
    /**
     * Test SequenceReader class
     */
    public static void testSequenceReader() {
        SequenceReader r = new SequenceReader(true);
        r.indexFASTAFile("/Users/leggettr/Documents/Projects/Nanopore/test.fasta", null, true);
        String s = r.getSubSequence("gi|223667766|ref|NZ_DS264586.1|", 0, 499);
        System.out.println("String (0,499) = ["+s+"]");
        s = r.getSubSequence("gi|223667766|ref|NZ_DS264586.1|", 0, 9);
        System.out.println("String (0,9) = ["+s+"]");
        s = r.getSubSequence("gi|223667766|ref|NZ_DS264586.1|", 200, 209);
        System.out.println("String (200,209) = ["+s+"]");
        s = r.getSubSequence("gi|223667766|ref|NZ_DS264586.1|", 200, 214);
        System.out.println("String (200,214) = ["+s+"]");
    }
    
    /**
     * Test parser
     * @param options
     * @param overallStats
     * @param references 
     */
    public static void testParser(NanoOKOptions options, OverallStats overallStats, References references) {
        AlignmentFileParser p = new LastParser(options, references);
        AlignmentsTableFile nonAlignedSummary = new AlignmentsTableFile("blob.txt");
        //p.parseFile("/Users/leggettr/Documents/Projects/Nanopore/N79681_EvenMC_R7_06082014/last/2D/N79681_EvenMC_R7_0608215_5314_1_ch319_file116_strand.fast5_BaseCalled_2D.fasta.maf", nonAlignedSummary, overallStats);
        //System.exit(0);
    }
    
    private static void analyse(NanoOKOptions options) throws InterruptedException {
        OverallStats overallStats = new OverallStats(options);
        options.getReferences().setOverallStats(overallStats);

        // Load reference data
        options.getReferences().loadReferences();
        options.setReadFormat(options.getParser().getReadFormat());
        options.initialiseAlignmentSummaryFile();
        
        System.out.println("");
        
        // Parse all reads sets       
        if (options.doParseAlignments()) {
            ReadLengthsSummaryFile summary = new ReadLengthsSummaryFile(options.getLengthSummaryFilename());
            summary.open(options.getSample());
            
            for (int type = 0; type<3; type++) {
                if (options.isProcessingReadType(type)) {
                    System.out.println("Parsing " + NanoOKOptions.getTypeFromInt(type));
                    ReadSet readSet = new ReadSet(type, options, overallStats.getStatsByType(type));
                    int nReads = readSet.processReads();

                    if (nReads < 1) {
                        System.out.println("Error: unable to find any " + NanoOKOptions.getTypeFromInt(type) + " reads to process.");
                        System.out.println("");
                        System.exit(1);
                    }

                    int nReadsWithAlignments = readSet.getStats().getNumberOfReadsWithAlignments();
                    if (nReadsWithAlignments < 1) {
                        System.out.println("");
                        System.out.println("Error: unable to find any " + NanoOKOptions.getTypeFromInt(type) + " alignments to process.");
                        System.out.println("Common reasons for this:");
                        System.out.println("1. Failure to index the reference with the alignment tool, resulting in alignment files of 0 bytes");
                        System.out.println("2. Wrong reference specified to the align stage, resulting in no alignments");
                        System.out.println("3. When indexing with LAST, the output prefix needs to be the same as the reference FASTA file, minus the .fasta extension");
                        System.out.println("   e.g. lastdb -Q 0 referencename referencename.fasta");
                        System.out.println("");
                        System.exit(1);
                    } else if (nReadsWithAlignments < 400) {
                        System.out.println("Warning: not many alignments ("+nReadsWithAlignments+") found to process.");
                    }

                    summary.addReadSetStats(overallStats.getStatsByType(type));
                    overallStats.getStatsByType(type).closeKmersFile();
                    overallStats.getStatsByType(type).writeSubstitutionStats();
                    overallStats.getStatsByType(type).writeErrorMotifStats();
                    
                    int ignoredDuplicates = overallStats.getStatsByType(type).getIgnoredDuplicates();
                    if (ignoredDuplicates > 0) {
                        System.out.println(ignoredDuplicates + " ignored duplicate read IDs.");
                    }
                    
                    System.out.println("");
                    
                }
            }
            summary.close();            
            
            // Write files
            System.out.println("Writing analysis files");
            Set<String> ids = options.getReferences().getAllIds();
            int allCount = 3; //ids.size() * 3;
            int counter = 1;            
            for (int type=0; type<3; type++) {
                long completed = counter;
                long total = allCount;
                long e = 0;
                long s = NanoOKOptions.PROGRESS_WIDTH;

                if (total > 0) {
                    e = NanoOKOptions.PROGRESS_WIDTH * completed / total;
                    s = NanoOKOptions.PROGRESS_WIDTH - e;
                }
                                
                System.out.print("\r[");
                for (int i=0; i<e; i++) {
                    System.out.print("=");
                }
                for (int i=0; i<s; i++) {
                    System.out.print(" ");
                }
                System.out.print("] " + completed +"/" +  total);                
                options.getReferences().writeReferenceStatFiles(type);
                options.getReferences().writeReferenceSummary(type);
                counter++;
            }
            System.out.println("");

            System.out.println("Writing object");
            try {
                FileOutputStream fos = new FileOutputStream(options.getAnalysisDir() + File.separator + "OverallStats.ser");
                ObjectOutputStream oos = new ObjectOutputStream(fos);
                oos.writeObject(overallStats);
                oos.close();
            } catch (Exception e) {
                System.out.println("Exception trying to write object:");
                e.printStackTrace();
            }
        
        }
        
        // Plot graphs
        if (options.doPlotGraphs()) {
            System.out.println("");
            System.out.println("Plotting graphs");
            RGraphPlotter plotter = new RGraphPlotter(options);
            plotter.plot(false);                
        }
        
        // Make report
        if (options.doMakeReport()) {
            System.out.println("");
            System.out.println("Making report");
            SampleReportWriter rw = new SampleReportWriter(options, overallStats);
            rw.writeReport();

            if (options.doMakePDF()) {
                System.out.println("");
                System.out.println("Making PDF");
                rw.makePDF();
            }
        }
                
        System.out.println("");
        System.out.println("Done");
    }
    
    private static void extract(NanoOKOptions options) throws InterruptedException {
        ReadExtractor re = new ReadExtractor(options);
        re.createDirectories();
        re.extract();
    }
    
    private static void align(NanoOKOptions options) throws InterruptedException {
        AlignmentFileParser parser = options.getParser();
        parser.checkForIndex(options.getReferenceFile().substring(0, options.getReferenceFile().lastIndexOf('.')));
        ReadAligner aligner = new ReadAligner(options, parser);
        options.setReadFormat(parser.getReadFormat());
        aligner.createDirectories();
        aligner.align();
    }
    
    private static void compare(NanoOKOptions options) throws InterruptedException {
        System.out.println("Comparing");
        SampleComparer comparer = new SampleComparer(options);
        comparer.loadSamples();
        comparer.compareSamples();
        
        options.setReferences(comparer.getSample(0).getStatsByType(0).getOptions().getReferences());

        System.out.println("");
        System.out.println("Plotting graphs");
        RGraphPlotter plotter = new RGraphPlotter(options);
        plotter.plot(true);   
        
        System.out.println("");
        System.out.println("Making PDF");
        ComparisonReportWriter crw = new ComparisonReportWriter(options, comparer);
        crw.writeReport();
        crw.makePDF();
    }
    
    private static void memoryReport() {
        Runtime runtime = Runtime.getRuntime();
        long mb = 1024 * 1024;
        long totalMem = runtime.totalMemory() / mb;
        long maxMem = runtime.maxMemory() / mb;
        long freeMem = runtime.freeMemory() / mb;
        System.out.println("totalMem: " + totalMem + "Mb");
        System.out.println("  maxMem: " + maxMem + "Mb");
        System.out.println(" freeMem: " + freeMem + "Mb");
    }
    
    /**
     * Entry to tool.
     * @param args command line arguments
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("");
        System.out.println("NanoOK " + VERSION_STRING);
        System.out.println("");

        NanoOKOptions options = new NanoOKOptions();
               
        Locale.setDefault(new Locale("en", "US"));
        
        // Parse command line
        options.parseArgs(args);
        options.checkDirectoryStructure();

        // Check dependencies
        System.out.println("");
        System.out.println("Checking dependencies");
        checkDependencies();
        
        if (options.getRunMode() == NanoOKOptions.MODE_EXTRACT) {
            extract(options);
        } else if (options.getRunMode() == NanoOKOptions.MODE_ALIGN) {
            align(options);
        } else if (options.getRunMode() == NanoOKOptions.MODE_ANALYSE) {
            analyse(options);
        } else if (options.getRunMode() == NanoOKOptions.MODE_COMPARE) {
            compare(options);
        }
        
        //memoryReport();
        
        options.getLog().close();
    }
}
