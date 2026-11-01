import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.reference.FastaSequenceIndexCreator;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

public final class GenerateHtsStressData {
    private static final long GIB = 1024L * 1024L * 1024L;
    private static final char[] RANDOM_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final byte[] BASES = "ACGT".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] QUALITIES = "ABCDEFGHI".getBytes(StandardCharsets.US_ASCII);

    private GenerateHtsStressData() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            usage();
            return;
        }
        String command = args[0].toLowerCase(Locale.ROOT);
        switch (command) {
            case "bam" -> createBam(
                    Path.of(args[1]),
                    args.length > 2 ? Long.parseLong(args[2]) : GIB,
                    args.length > 3 ? Long.parseLong(args[3]) : 1_000_000_000L
            );
            case "vcf" -> createVcf(
                    Path.of(args[1]),
                    args.length > 2 ? Long.parseLong(args[2]) : GIB
            );
            case "cram" -> createCram(
                    Path.of(args[1]),
                    Path.of(args[2]),
                    Path.of(args[3])
            );
            case "reference" -> createReference(
                    Path.of(args[1]),
                    args.length > 2 ? Integer.parseInt(args[2]) : 1_000_000
            );
            default -> usage();
        }
    }

    private static void createReference(Path output, int bases) throws Exception {
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(output)) {
            writer.write(">chr1\n");
            StringBuilder line = new StringBuilder(80);
            for (int index = 0; index < bases; index++) {
                line.append((char) BASES[index & 3]);
                if (line.length() == 80) {
                    writer.write(line.toString());
                    writer.newLine();
                    line.setLength(0);
                }
            }
            if (!line.isEmpty()) {
                writer.write(line.toString());
                writer.newLine();
            }
        }
        FastaSequenceIndexCreator.create(output, true);
        Path fastaIndex = output.resolveSibling(output.getFileName() + ".fai");
        Path dictionary = output.resolveSibling(
                stripFastaSuffix(output.getFileName().toString()) + ".dict");
        Files.writeString(
                dictionary,
                "@HD\tVN:1.6\tSO:unsorted\n"
                        + "@SQ\tSN:chr1\tLN:" + bases + "\n",
                StandardCharsets.US_ASCII
        );
        System.out.printf("reference=%s bases=%d bytes=%d fai=%s dict=%s%n",
                output, bases, Files.size(output), fastaIndex, dictionary);
    }

    private static void createBam(
            Path output,
            long targetBytes,
            long contigLength
    ) throws Exception {
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.deleteIfExists(output);
        Files.deleteIfExists(output.resolveSibling(stripSuffix(output.getFileName().toString()) + ".bai"));
        Files.deleteIfExists(output.resolveSibling(output.getFileName() + ".bai"));

        SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        header.setSequenceDictionary(new SAMSequenceDictionary(
                List.of(new SAMSequenceRecord("chr1", Math.toIntExact(contigLength)))
        ));
        long start = System.nanoTime();
        long records = 0;
        SplittableRandom random = new SplittableRandom(20261101L);
        try (SAMFileWriter writer = new SAMFileWriterFactory()
                .setCreateIndex(true)
                .setUseAsyncIo(true)
                .makeWriter(header, true, output, null)) {
            while (records < 40_000_000L) {
                long position = 1 + records * 40L;
                if (position + 100L > contigLength) {
                    throw new IOException("Contig is too short for generated BAM records.");
                }
                SAMRecord record = new SAMRecord(header);
                record.setReadName("stress_" + records);
                record.setFlags(0);
                record.setReferenceName("chr1");
                record.setAlignmentStart(Math.toIntExact(position));
                record.setMappingQuality(60);
                record.setCigarString("100M");
                byte[] bases = new byte[100];
                byte[] qualities = new byte[100];
                for (int index = 0; index < bases.length; index++) {
                    bases[index] = BASES[random.nextInt(BASES.length)];
                    qualities[index] = QUALITIES[random.nextInt(QUALITIES.length)];
                }
                record.setReadBases(bases);
                record.setBaseQualities(qualities);
                writer.addAlignment(record);
                records++;
                if (records % 500_000L == 0) {
                    System.out.printf("bam records=%d bytes=%d%n", records, Files.size(output));
                    if (Files.size(output) >= targetBytes) {
                        break;
                    }
                }
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        System.out.printf(
                "bam=%s records=%d bytes=%d targetBytes=%d elapsedMs=%d index=%s%n",
                output,
                records,
                Files.size(output),
                targetBytes,
                elapsedMs,
                Files.exists(output.resolveSibling(output.getFileName() + ".bai"))
                        || Files.exists(output.resolveSibling(
                        stripSuffix(output.getFileName().toString()) + ".bai"))
        );
    }

    private static void createVcf(Path output, long targetBytes) throws Exception {
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (CountingOutputStream counting = new CountingOutputStream(
                new BufferedOutputStream(Files.newOutputStream(output)));
             BufferedWriter writer = new BufferedWriter(
                     new java.io.OutputStreamWriter(counting, StandardCharsets.UTF_8),
                     1 << 20)) {
            writer.write("##fileformat=VCFv4.2\n");
            writer.write("##contig=<ID=chr1,length=1000000000>\n");
            writer.write("##INFO=<ID=NOTE,Number=1,Type=String,Description=\"Stress payload\">\n");
            writer.write("##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n");
            writer.write("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tS1\tS2\n");
            SplittableRandom random = new SplittableRandom(20261101L);
            long variants = 0;
            StringBuilder line = new StringBuilder(256);
            while (variants < 20_000_000L && counting.count < targetBytes) {
                line.setLength(0);
                line.append("chr1\t").append(variants + 1)
                        .append("\t.\tA\tG\t50\tPASS\tNOTE=");
                for (int index = 0; index < 96; index++) {
                    line.append(RANDOM_ALPHABET[random.nextInt(RANDOM_ALPHABET.length)]);
                }
                line.append("\tGT\t0/1\t./.\n");
                writer.write(line.toString());
                variants++;
                if (variants % 500_000L == 0) {
                    writer.flush();
                    System.out.printf(
                            "vcf variants=%d bytes=%d targetBytes=%d%n",
                            variants,
                            counting.count,
                            targetBytes
                    );
                }
            }
            writer.flush();
            System.out.printf(
                    "vcf=%s variants=%d bytes=%d targetBytes=%d%n",
                    output,
                    variants,
                    counting.count,
                    targetBytes
            );
        }
    }

    private static void createCram(Path bam, Path reference, Path output) throws Exception {
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (SamReader reader = SamReaderFactory.makeDefault().open(bam);
             SAMFileWriter writer = new SAMFileWriterFactory()
                     .setUseAsyncIo(false)
                     .makeCRAMWriter(reader.getFileHeader(), true, output, reference)) {
            for (SAMRecord record : reader) {
                writer.addAlignment(record);
            }
        }
        System.out.printf("cram=%s bytes=%d reference=%s%n",
                output, Files.size(output), reference);
    }

    private static String stripSuffix(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".bam") ? name.substring(0, name.length() - 4) : name;
    }

    private static String stripFastaSuffix(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".fasta")) {
            return name.substring(0, name.length() - 6);
        }
        if (lower.endsWith(".fa")) {
            return name.substring(0, name.length() - 3);
        }
        return name;
    }

    private static void usage() {
        System.out.println("""
                Usage:
                  bam <output.bam> [targetBytes] [contigLength]
                  vcf <output.vcf> [targetBytes]
                  reference <reference.fa> [baseCount]
                  cram <input.bam> <reference.fa> <output.cram>
                """);
    }

    private static final class CountingOutputStream extends FilterOutputStream {
        private long count;

        private CountingOutputStream(OutputStream output) {
            super(output);
        }

        @Override
        public void write(int value) throws IOException {
            out.write(value);
            count++;
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            out.write(buffer, offset, length);
            count += length;
        }
    }
}
