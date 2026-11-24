# Organism

A minimal self-replicating Java program for experimenting with artificial life.

`Organism.java` is a single source file that reproduces each time it is run. It reads its own source code as its genome, writes out one offspring with a unique hierarchical ID, checks that the offspring is viable by compiling it, records the birth in a lineage log, and then rewrites its own source to remember how many children it has had.

No external libraries, no build tool: just the JDK.

## How it works

The program is split along biological lines:

| Concept | In the program |
|---|---|
| **Genome** | The organism's own source code |
| **Genes** | Four constants (`ID`, `PARENT_ID`, `GENERATION`, `CHILDREN`) rewritten at each birth |
| **Body** | The running JVM process |
| **Birth** | Writing a modified copy of the genome to `world/<child-id>/Organism.java` |
| **Viability** | The child is compiled with `javax.tools.JavaCompiler`; a child that fails to compile is stillborn and removed |
| **Ageing** | The parent rewrites its own `CHILDREN` gene after each birth |
| **Fossil record** | `world/lineage.csv`, one line per birth |

Each run follows the same lifecycle:

1. **Read the genome.** The organism locates its own source file (via the source launcher's `jdk.launcher.sourcefile` property, falling back to a command-line argument or `Organism.java`).
2. **Build the child's genome.** The identity genes are replaced: the child's `ID` is the parent's ID plus a child number, its `PARENT_ID` is the parent's ID, its `GENERATION` is one higher, and its `CHILDREN` count starts at zero.
3. **Give birth.** The child is written to its own folder under `world/`.
4. **Check viability.** The child is compiled into a temporary folder, which is then deleted.
5. **Record the birth** in `world/lineage.csv`.
6. **Age.** The parent increments its own `CHILDREN` gene so its next child gets the next number.

### Unique IDs

IDs are hierarchical, so every ID encodes its own ancestry:

- `0` is the founder
- `0.2` is the founder's second child
- `0.2.1` is the first child of `0.2`

Because each organism keeps count of its own children in its genome, IDs are unique without any central registry.

### Genome integrity

Genes are located by whole-line matches on their declarations, and each must appear exactly once. If a gene is missing or duplicated, the organism refuses to reproduce rather than produce a damaged offspring.

## Requirements

- **A JDK, version 11 or later.** Developed and tested on OpenJDK 21.
- A JDK rather than a JRE is needed for the viability check. Without a compiler, births are still recorded but marked as viability unknown.

## Usage

Put `Organism.java` in an empty folder and **always run commands from that folder**, since the `world/` directory is resolved relative to where you run `java`.

```bash
java Organism.java                  # the founder reproduces: 0.1, then 0.2, ...
java world/0.1/Organism.java        # organism 0.1 reproduces: 0.1.1
java world/0.1.1/Organism.java      # organism 0.1.1 reproduces: 0.1.1.1
java Organism.java --tree           # print the family tree
```

Example output:

```
Organism 0.1 (generation 1) gave birth to 0.1.1 [viable]
  Run it with: java world/0.1.1/Organism.java
```

Example family tree:

```
0 (founder)
|-- 0.1
|   `-- 0.1.1
|       `-- 0.1.1.1
`-- 0.2
```

Example `world/lineage.csv`:

```
id,parent,generation,born,viable
0.1,0,1,2026-10-23T12:02:51.089,true
0.2,0,1,2026-10-23T12:02:52.412,true
0.1.1,0.1,2,2026-10-23T12:02:53.650,true
```

### Folder layout after a few generations

```
Organism.java              the founder (its CHILDREN gene now records 2 births)
world/
  lineage.csv
  0.1/Organism.java
  0.1.1/Organism.java
  0.1.1.1/Organism.java
  0.2/Organism.java
```

To start a fresh experiment, delete `world/` and restore the founder's `CHILDREN` gene to `0` (or use a fresh copy of `Organism.java`).

## Safety

Reproduction is **passive**: offspring are written to disk but never launched automatically. The population only grows when you choose to run an organism, so there is no risk of runaway exponential growth.

As a further limit, an organism will not reproduce once `world/lineage.csv` records `MAX_POPULATION` births (500 by default).

If you change the program so that organisms launch their own offspring (for example with `ProcessBuilder`), keep hard limits in place, such as a maximum generation, a population cap and a lifespan. Without them, the program behaves like a fork bomb.

## Configuration

These constants near the top of `Organism.java` can be edited before running the founder:

| Constant | Default | Meaning |
|---|---|---|
| `WORLD` | `world` | Folder where offspring and the lineage log live |
| `MAX_POPULATION` | `500` | Maximum number of recorded births |

Edit the four genes only if you want a different founder. Each must stay on its own line, in the form `static final String ID = "...";` or `static final int GENERATION = ...;`.

## Ideas for extending it

- **Mutation.** Randomly alter a numeric "trait" gene, or occasionally corrupt a character in the genome, at each birth. The compile step then acts as natural selection, removing non-viable mutants.
- **Death.** Give organisms a lifespan in births or time, after which they delete themselves.
- **Active reproduction.** Have children launched automatically, with the limits described under Safety.
- **True self-replication.** Rewrite the organism as a quine that rebuilds its source from an internal description instead of reading its own file, in the spirit of von Neumann's self-reproducing automata.
- **Phylogeny analysis.** Load `lineage.csv` into a spreadsheet or graph tool to study branching and generation depth.
