package io.opentools.external.tool;

import java.util.List;

public final class ExternalToolSpecs {
    public static final ExecutableSpec MAKEBLASTDB = new ExecutableSpec(
            "blast.makeblastdb",
            "NCBI makeblastdb",
            "OPENTOOLS_MAKEBLASTDB",
            List.of("makeblastdb"),
            List.of("-version"),
            List.of("tools/blast/bin")
    );
    public static final ExecutableSpec DIAMOND = new ExecutableSpec(
            "diamond",
            "DIAMOND",
            "OPENTOOLS_DIAMOND",
            List.of("diamond"),
            List.of("version"),
            List.of("tools/diamond")
    );
    public static final ExecutableSpec HMMSCAN = new ExecutableSpec(
            "hmmer.hmmscan",
            "HMMER hmmscan",
            "OPENTOOLS_HMMSCAN",
            List.of("hmmscan"),
            List.of("-h"),
            List.of("tools/hmmer/bin", "tools/hmmer")
    );
    public static final ExecutableSpec HMMSEARCH = new ExecutableSpec(
            "hmmer.hmmsearch",
            "HMMER hmmsearch",
            "OPENTOOLS_HMMSEARCH",
            List.of("hmmsearch"),
            List.of("-h"),
            List.of("tools/hmmer/bin", "tools/hmmer")
    );
    public static final ExecutableSpec MUSCLE = new ExecutableSpec(
            "alignment.muscle",
            "MUSCLE",
            "OPENTOOLS_MUSCLE",
            List.of("muscle"),
            List.of("-version"),
            List.of("tools/muscle")
    );
    public static final ExecutableSpec TRIMAL = new ExecutableSpec(
            "alignment.trimal",
            "trimAl",
            "OPENTOOLS_TRIMAL",
            List.of("trimal"),
            List.of("--version"),
            List.of("tools/trimal")
    );
    public static final ExecutableSpec IQTREE = new ExecutableSpec(
            "alignment.iqtree",
            "IQ-TREE",
            "OPENTOOLS_IQTREE",
            List.of("iqtree2", "iqtree"),
            List.of("--version"),
            List.of("tools/iqtree/bin", "tools/iqtree")
    );
    public static final ExecutableSpec MCSCANX = new ExecutableSpec(
            "comparative.mcscanx",
            "MCScanX",
            "OPENTOOLS_MCSCANX",
            List.of("MCScanX"),
            List.of("-h"),
            List.of("tools/mcscanx")
    );

    private ExternalToolSpecs() {
    }

    public static ExecutableSpec blast(String executableName) {
        return new ExecutableSpec(
                "blast." + executableName,
                "NCBI " + executableName,
                "OPENTOOLS_" + executableName.toUpperCase(),
                List.of(executableName),
                List.of("-version"),
                List.of("tools/blast/bin")
        );
    }
}
