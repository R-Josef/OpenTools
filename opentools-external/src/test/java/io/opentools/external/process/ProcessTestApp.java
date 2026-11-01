package io.opentools.external.process;

public final class ProcessTestApp {
    private ProcessTestApp() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("sleep")) {
            System.out.println("started");
            Thread.sleep(10_000);
            return;
        }
        System.out.println(args.length == 0 ? "no-args" : String.join(" | ", args));
        System.err.println("stderr-line");
    }
}
