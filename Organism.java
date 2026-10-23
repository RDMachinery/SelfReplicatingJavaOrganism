import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Organism - a minimal self-replicating Java program for artificial-life experiments.
 *
 * Each time it is executed, it:
 *   1. reads its own source code (its genome),
 *   2. creates one offspring with a unique hierarchical ID (e.g. 0.2.1 = first child
 *      of the second child of the founder),
 *   3. checks the offspring is viable by compiling it,
 *   4. records the birth in world/lineage.csv,
 *   5. rewrites its own source to remember how many children it has had (ageing).
 *
 * Run (Java 11+, always from the same root folder):
 *   java Organism.java                    founder reproduces
 *   java world/0.1/Organism.java          a descendant reproduces
 *   java Organism.java --tree             print the family tree
 *
 * Reproduction is passive: offspring are created but never launched automatically,
 * so the population only grows when you choose to run an organism.
 *
 * @author Mario Gianota <mariogianota@protonmail.com>
 *
 */
class Organism {

    // ---- Genes: rewritten during reproduction. Keep each on its own line. ----
    static final String ID = "0";
    static final String PARENT_ID = "-";
    static final int GENERATION = 0;
    static final int CHILDREN = 0;

    // ---- Environment ----
    static final Path WORLD = Path.of("world");
    static final Path LINEAGE = WORLD.resolve("lineage.csv");
    static final int MAX_POPULATION = 500;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--tree")) {
            printTree();
            return;
        }

        Path self = locateSelf(args);
        String genome = Files.readString(self);

        if (population() >= MAX_POPULATION) {
            System.out.println("Organism " + ID + ": the world is full (" + MAX_POPULATION
                    + "). No offspring.");
            return;
        }

        // 1. Build the offspring's genome.
        int childNumber = CHILDREN + 1;
        String childId = ID + "." + childNumber;
        String childGenome = genome;
        childGenome = setString(childGenome, "ID", childId);
        childGenome = setString(childGenome, "PARENT_ID", ID);
        childGenome = setInt(childGenome, "GENERATION", GENERATION + 1);
        childGenome = setInt(childGenome, "CHILDREN", 0);

        // 2. Give birth: each organism lives in its own folder.
        Path childDir = Files.createDirectories(WORLD.resolve(childId));
        Path childFile = childDir.resolve("Organism.java");
        Files.writeString(childFile, childGenome);

        // 3. Viability check: a child that does not compile is stillborn.
        Boolean viable = compiles(childFile);
        if (Boolean.FALSE.equals(viable)) {
            Files.deleteIfExists(childFile);
            Files.deleteIfExists(childDir);
        }

        // 4. Record the birth.
        recordBirth(childId, viable);

        // 5. Age: remember that this organism has had another child.
        Files.writeString(self, setInt(genome, "CHILDREN", childNumber));

        String status = viable == null ? "viability unknown (no compiler found)"
                      : viable ? "viable" : "stillborn";
        System.out.printf("Organism %s (generation %d) gave birth to %s [%s]%n",
                ID, GENERATION, childId, status);
        if (Boolean.TRUE.equals(viable)) {
            System.out.println("  Run it with: java " + childFile);
        }
    }

    /** Finds this organism's source file. */
    static Path locateSelf(String[] args) {
        String launched = System.getProperty("jdk.launcher.sourcefile"); // source-file mode
        if (launched != null) return Path.of(launched);
        if (args.length > 0) return Path.of(args[0]);
        return Path.of("Organism.java");
    }

    /** Replaces the value of a String gene, insisting it occurs exactly once. */
    static String setString(String src, String name, String value) {
        Pattern p = Pattern.compile("(?m)^(\\s*static final String " + name + " = )\"[^\"]*\";");
        return replaceGene(src, name, p, "\"" + value + "\";");
    }

    /** Replaces the value of an int gene, insisting it occurs exactly once. */
    static String setInt(String src, String name, int value) {
        Pattern p = Pattern.compile("(?m)^(\\s*static final int " + name + " = )-?\\d+;");
        return replaceGene(src, name, p, value + ";");
    }

    static String replaceGene(String src, String name, Pattern p, String newValue) {
        Matcher m = p.matcher(src);
        if (!m.find()) throw new IllegalStateException("Genome damaged: gene " + name + " not found");
        int start = m.start(), end = m.end();
        String prefix = m.group(1);
        if (m.find()) throw new IllegalStateException("Genome damaged: gene " + name + " duplicated");
        return src.substring(0, start) + prefix + newValue + src.substring(end);
    }

    /** Compiles the offspring into a throwaway folder. Returns null if no compiler exists. */
    static Boolean compiles(Path source) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) return null;
        Path out = Files.createTempDirectory("organism-check");
        try {
            int result = javac.run(null, null, System.err, "-d", out.toString(), source.toString());
            return result == 0;
        } finally {
            try (var files = Files.walk(out)) {
                files.sorted(Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
            }
        }
    }

    static void recordBirth(String childId, Boolean viable) throws IOException {
        Files.createDirectories(WORLD);
        if (!Files.exists(LINEAGE)) {
            Files.writeString(LINEAGE, "id,parent,generation,born,viable\n");
        }
        String born = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String line = String.join(",", childId, ID, String.valueOf(GENERATION + 1), born,
                String.valueOf(viable)) + "\n";
        Files.writeString(LINEAGE, line, StandardOpenOption.APPEND);
    }

    static int population() throws IOException {
        if (!Files.exists(LINEAGE)) return 0;
        return Math.max(0, Files.readAllLines(LINEAGE).size() - 1);
    }

    /** Prints the family tree recorded in lineage.csv. */
    static void printTree() throws IOException {
        if (!Files.exists(LINEAGE)) {
            System.out.println("No births recorded yet.");
            return;
        }
        Map<String, List<String>> children = new TreeMap<>();
        Map<String, String> status = new HashMap<>();
        for (String line : Files.readAllLines(LINEAGE).subList(1, population() + 1)) {
            String[] f = line.split(",");
            children.computeIfAbsent(f[1], k -> new ArrayList<>()).add(f[0]);
            status.put(f[0], f[4]);
        }
        System.out.println("0 (founder)");
        printBranch("0", "", children, status);
    }

    static void printBranch(String id, String indent, Map<String, List<String>> children,
                            Map<String, String> status) {
        List<String> kids = children.getOrDefault(id, List.of());
        for (int i = 0; i < kids.size(); i++) {
            boolean last = i == kids.size() - 1;
            String kid = kids.get(i);
            String mark = "false".equals(status.get(kid)) ? "  (stillborn)" : "";
            System.out.println(indent + (last ? "`-- " : "|-- ") + kid + mark);
            printBranch(kid, indent + (last ? "    " : "|   "), children, status);
        }
    }
}
