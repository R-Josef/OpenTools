package io.opentools.external.alignment;

import io.opentools.external.sequence.SequenceKind;

public enum BlastProgram {
    BLASTN("blastn", "nucl"),
    BLASTP("blastp", "prot"),
    BLASTX("blastx", "prot"),
    TBLASTN("tblastn", "nucl"),
    TBLASTX("tblastx", "nucl");

    private final String executable;
    private final String subjectDatabaseType;

    BlastProgram(String executable, String subjectDatabaseType) {
        this.executable = executable;
        this.subjectDatabaseType = subjectDatabaseType;
    }

    public String executable() {
        return executable;
    }

    public String subjectDatabaseType() {
        return subjectDatabaseType;
    }

    public static BlastProgram auto(SequenceKind query, SequenceKind subject) {
        if (query == SequenceKind.NUCLEOTIDE && subject == SequenceKind.NUCLEOTIDE) {
            return BLASTN;
        }
        if (query == SequenceKind.PROTEIN && subject == SequenceKind.PROTEIN) {
            return BLASTP;
        }
        if (query == SequenceKind.NUCLEOTIDE && subject == SequenceKind.PROTEIN) {
            return BLASTX;
        }
        if (query == SequenceKind.PROTEIN && subject == SequenceKind.NUCLEOTIDE) {
            return TBLASTN;
        }
        return null;
    }
}
