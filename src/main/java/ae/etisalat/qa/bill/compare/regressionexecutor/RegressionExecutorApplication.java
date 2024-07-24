package ae.etisalat.qa.bill.compare.regressionexecutor;

import de.redsix.pdfcompare.CompareResultImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import de.redsix.pdfcompare.PdfComparator;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@SpringBootApplication
public class RegressionExecutorApplication {

    @Value("${folder1.path}")
    private String folder1Path;

    @Value("${folder2.path}")
    private String folder2Path;

    @Value("${output.path}")
    private String outputPath;

    @Value("${pdf.ignore.config.path}")
    private String pdf_ignore_config;

    @Value("${control.file.extension}")
    private String control_file_extension;

    public static void main(String[] args) {
        SpringApplication.run(RegressionExecutorApplication.class, args);
    }

    @Bean
    public CommandLineRunner compareFiles() {
        Logger logger = LoggerFactory.getLogger(RegressionExecutorApplication.class);
        return new CommandLineRunner() {
            @Override
            public void run(String... args) throws Exception {
                File folder1 = new File(folder1Path);
                File folder2 = new File(folder2Path);

                if (folder1.isDirectory() && folder2.isDirectory()) {

// compare PDF starts here
                    FilenameFilter pdfFilter = new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            return name.toLowerCase().endsWith(".pdf");
                        }
                    };
                    Set<String> folder1Files = new HashSet<>(Arrays.asList(Objects.requireNonNull(folder1.list(pdfFilter))));
                    Set<String> folder2Files = new HashSet<>(Arrays.asList(Objects.requireNonNull(folder2.list(pdfFilter))));
                    Set<String> commonPdfFiles = new HashSet<>(folder1Files);
                    commonPdfFiles.retainAll(folder2Files);

                    System.out.println("Common files: " + commonPdfFiles);
                    System.out.println("Files only in folder 1: " + folder1Files);
                    System.out.println("Files only in folder 2: " + folder2Files);
                    compareTheFiles(folder1Files,folder2Files,commonPdfFiles,"pdf", logger);

// compare Unified XML starts here
                    FilenameFilter xmlFilter = new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            return name.toLowerCase().endsWith(".xml");
                        }
                    };

                    Set<String> folder1Files_xml = new HashSet<>(Arrays.asList(Objects.requireNonNull(folder1.list(xmlFilter))));
                    Set<String> folder2Files_xml = new HashSet<>(Arrays.asList(Objects.requireNonNull(folder2.list(xmlFilter))));
                    Set<String> commonXmlFiles = new HashSet<>(folder1Files_xml);
                    commonXmlFiles.retainAll(folder2Files_xml);

                    System.out.println("XML Common files: " + commonXmlFiles);
                    System.out.println("XML Files only in folder 1: " + folder1Files_xml);
                    System.out.println("XML Files only in folder 2: " + folder2Files_xml);
                    compareTheFiles(folder1Files_xml,folder2Files_xml,commonXmlFiles,"xml", logger);

                    // compare Control starts here
                    FilenameFilter controlFilter = new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            return name.toLowerCase().endsWith("."+control_file_extension);
                        }
                    };

                    Set<String> folder1Files_control = new HashSet<>(Arrays.asList(Objects.requireNonNull(folder1.list(controlFilter))));
                    Set<String> folder2Files_control = new HashSet<>(Arrays.asList(Objects.requireNonNull(folder2.list(controlFilter))));
                    Set<String> commoncontrolFiles = new HashSet<>(folder1Files_control);
                    commonXmlFiles.retainAll(folder2Files_xml);
                    compareTheFiles(folder1Files_control,folder2Files_control,commoncontrolFiles,"ctl", logger);

                } else {
                    System.out.println("One or both of the provided paths are not directories.");
                }
            }
        };
    }

    public void compareTheFiles(Set<String> folder1Files, Set<String> folder2Files, Set<String> commonFiles, String filetype, Logger logger) throws Exception {
        for (String file : commonFiles) {
            String filePath_1 = folder1Path + File.separator + file;
            System.out.println(filePath_1);
            String filePath_2 = folder2Path + File.separator + file;
            System.out.println(filePath_2);
            if (filetype.equalsIgnoreCase("pdf")){
                logger.error("**********************");
                logger.error("*PDF Comparison start*");
                logger.error("**********************");
                comparePDF(filePath_1,filePath_2,outputPath+ File.separator + "diff_"+file, logger);
            }
            else if (filetype.equalsIgnoreCase("xml")){
                logger.error("**********************");
                logger.error("*XML Comparison start*");
                logger.error("**********************");
                compareXML(filePath_1,filePath_2,outputPath+ File.separator + "diff_"+file, logger);
            }
            else if (filetype.equalsIgnoreCase("ctl")){
                logger.error("**************************");
                logger.error("*CONTROL Comparison start*");
                logger.error("**************************");
                compareCTL(filePath_1,filePath_2,outputPath+ File.separator + "diff_"+file, logger);
            }
        }
    }

    public void compareCTL(String PROD_FILE_PATH, String TEST_FILE_PATH, String outputPath, Logger logger) throws Exception {
        List<String> file1Lines = readLines(PROD_FILE_PATH);
        List<String> file2Lines = readLines(TEST_FILE_PATH);
        compareTextFiles(file1Lines, file2Lines, logger);
    }

    private static void compareTextFiles(List<String> file1Lines, List<String> file2Lines, Logger logger) {
        int maxLines = Math.max(file1Lines.size(), file2Lines.size());

        for (int i = 0; i < maxLines; i++) {
            String line1 = i < file1Lines.size() ? file1Lines.get(i) : "";
            String line2 = i < file2Lines.size() ? file2Lines.get(i) : "";

            if (!line1.equals(line2)) {
                logger.error("Difference at line %d:%n", i + 1);
                logger.error("File1: "+  line1);
                logger.error("File2: "+  line2);
            }
        }
    }

    private static List<String> readLines(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        return Files.readAllLines(path);
    }

    private static String readAndCleanXML(String filePath) throws Exception {
        // Read the XML file and remove any BOM or extraneous characters
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(filePath)), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line.trim());
            }
            return sb.toString();
        }
    }

    public void compareXML(String PROD_FILE_PATH, String TEST_FILE_PATH, String outputPath, Logger logger) throws Exception {
        Diff diff = DiffBuilder.compare(readAndCleanXML(PROD_FILE_PATH))
                .withTest(readAndCleanXML(TEST_FILE_PATH))
                .ignoreWhitespace()
                .checkForSimilar() // check for similar content, not exact match
                .build();
        if (diff.hasDifferences()) {
            System.out.println("Differences found in XML");
            String dif = "";
            logger.error("Difference fount in "+PROD_FILE_PATH);
            diff.getDifferences().forEach(difference -> logger.error(difference.toString()));
            logger.error("----------------------------------------------------------------------------------------");
        } else {
            System.out.println("No differences found.");
        }
    }

    public void comparePDF(String PROD_FILE_PATH, String TEST_FILE_PATH, String outputPath, Logger logger) throws Exception {
        String Status="";
        try {
            PdfComparator pdfComparator = 	new PdfComparator(PROD_FILE_PATH , TEST_FILE_PATH).withIgnore(pdf_ignore_config);
            CompareResultImpl result = pdfComparator.compare();
            System.out.println(result.getDifferencesJson());
            logger.error("Difference fount in "+PROD_FILE_PATH);
            logger.error(((result.getDifferencesJson()).replaceAll("[\\t\\n\\r]+"," ")).replaceAll("exclusions","difference"));
            logger.error("----------------------------------------------------------------------------------------");

            if (!result.isEqual()) {
                result.writeTo(outputPath);
                Status="not matching";
            }
            else {
                Status="matching";
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}