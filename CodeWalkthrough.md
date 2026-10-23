# Code Walkthrough: Organism.java

This document walks through [`Organism.java`](Organism.java) from top to bottom, with line numbers. Every library call is explained, including what it returns and how it behaves in edge cases where that matters to this program.

## Contents

- [Lines 1–8: imports](#lines-18-imports)
- [Lines 10–28: documentation comment](#lines-1028-documentation-comment)
- [Line 29: the class](#line-29-the-class)
- [Lines 31–35: the genes](#lines-3135-the-genes)
- [Lines 37–40: the environment](#lines-3740-the-environment)
- [Line 42: the entry point](#line-42-the-entry-point)
- [Lines 43–46: the `--tree` option](#lines-4346-the---tree-option)
- [Lines 48–49: reading the genome](#lines-4849-reading-the-genome)
- [Lines 51–55: the population cap](#lines-5155-the-population-cap)
- [Lines 57–64: building the child's genome](#lines-5764-building-the-childs-genome)
- [Lines 66–69: giving birth](#lines-6669-giving-birth)
- [Lines 71–76: the viability check](#lines-7176-the-viability-check)
- [Line 79: recording the birth](#line-79-recording-the-birth)
- [Line 82: ageing](#line-82-ageing)
- [Lines 84–90: reporting](#lines-8490-reporting)
- [Lines 93–99: `locateSelf`, finding its own source file](#lines-9399-locateself-finding-its-own-source-file)
- [Lines 101–105: `setString`, rewriting a text gene](#lines-101105-setstring-rewriting-a-text-gene)
- [Lines 107–111: `setInt`, rewriting a number gene](#lines-107111-setint-rewriting-a-number-gene)
- [Lines 113–120: `replaceGene`, the surgery](#lines-113120-replacegene-the-surgery)
- [Lines 122–135: `compiles`, the viability check](#lines-122135-compiles-the-viability-check)
- [Lines 137–146: `recordBirth`, the lineage log](#lines-137146-recordbirth-the-lineage-log)
- [Lines 148–151: `population`](#lines-148151-population)
- [Lines 153–168: `printTree`](#lines-153168-printtree)
- [Lines 170–180: `printBranch`, drawing the tree](#lines-170180-printbranch-drawing-the-tree)

## Lines 1–8: imports

```java
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
```

An `import` lets you use a class by its short name instead of its full package name. A `*` imports every class in that package.

- **Line 1, `IOException`**: the exception thrown when a file operation fails, for example a file can't be read.
- **Line 2, `java.nio.file.*`**: the modern file-handling API. The program uses three classes from it:
  - `Path` represents a file or folder location.
  - `Files` is a collection of static methods for reading, writing, creating and deleting files.
  - `StandardOpenOption` holds options such as "append".
- **Line 3, `LocalDateTime`**: represents a date and time without a time zone.
- **Line 4, `DateTimeFormatter`**: turns a date/time into text in a chosen format.
- **Line 5, `java.util.*`**: the collections library. The program uses `Map`, `List`, `TreeMap`, `HashMap`, `ArrayList` and `Comparator` from it.
- **Line 6, `java.util.regex.*`**: regular expressions. The program uses `Pattern`, a compiled search pattern, and `Matcher`, which runs a pattern over some text.
- **Lines 7–8, `JavaCompiler` and `ToolProvider`**: the JDK's built-in Java compiler, callable from inside a program.

## Lines 10–28: documentation comment

Everything between `/**` and `*/` is a comment, which the compiler ignores. It summarises what the program does and how to run it.

## Line 29: the class

```java
class Organism {
```

This declares the class. It is deliberately **not** `public`. With the `javac` compiler, a public class must live in a file with the same name. A non-public class has no such rule, so every offspring can keep the same class name in its own folder.

## Lines 31–35: the genes

```java
static final String ID = "0";
static final String PARENT_ID = "-";
static final int GENERATION = 0;
static final int CHILDREN = 0;
```

- `static` means the value belongs to the class itself, not to an object created from it. The program never creates an `Organism` object, so everything is static.
- `final` means the value can't change while the program runs. The values only change between runs, when the program rewrites the text of the source file.
- `String` is text; `int` is a whole number.

The four genes are:

- **`ID`**: this organism's identity. The founder is `"0"`.
- **`PARENT_ID`**: its parent's ID. The founder has none, hence `"-"`.
- **`GENERATION`**: how many steps it is from the founder.
- **`CHILDREN`**: how many offspring it has produced so far.

## Lines 37–40: the environment

```java
static final Path WORLD = Path.of("world");
static final Path LINEAGE = WORLD.resolve("lineage.csv");
static final int MAX_POPULATION = 500;
```

- **`Path.of("world")`** creates a `Path` object meaning "a folder called `world`". The path is relative, so it is interpreted from whatever folder you run `java` in. Creating a `Path` does **not** touch the disk; it is just a description of a location.
- **`WORLD.resolve("lineage.csv")`** joins two paths, giving `world/lineage.csv` (with the correct separator on Windows).
- **`MAX_POPULATION`** is the maximum number of births allowed.

## Line 42: the entry point

```java
public static void main(String[] args) throws Exception {
```

`main` is where Java starts running the program.

- `args` is an array holding any words you typed after the file name on the command line.
- `void` means it returns nothing.
- `throws Exception` means that if anything goes wrong and isn't handled, the program stops and prints the error. This is acceptable here, since there is nothing sensible to recover to.

## Lines 43–46: the `--tree` option

```java
if (args.length > 0 && args[0].equals("--tree")) {
    printTree();
    return;
}
```

- `args.length` is the number of command-line words.
- `.equals(...)` compares two strings by their content. In Java, `==` on strings checks whether they are the same object in memory, which isn't what you want.
- `&&` only evaluates the right-hand side if the left is true. That protects `args[0]` from failing when there are no arguments.
- If the first argument is `--tree`, the program prints the family tree and `return`s, which ends `main`, so no reproduction happens.

## Lines 48–49: reading the genome

```java
Path self = locateSelf(args);
String genome = Files.readString(self);
```

- **`locateSelf`** (explained at line 94) works out where this organism's source file is.
- **`Files.readString(path)`** opens the file, reads its entire contents as text (UTF-8 encoding), closes it, and returns the text as one `String`. That string is the genome.

## Lines 51–55: the population cap

```java
if (population() >= MAX_POPULATION) {
    System.out.println("Organism " + ID + ": the world is full (" + MAX_POPULATION
            + "). No offspring.");
    return;
}
```

- **`population()`** (line 148) counts the births recorded so far.
- **`System.out.println(...)`** prints a line of text to the console. `+` between strings joins them, and numbers are converted to text automatically.
- If the world is full, the program reports it and stops.

## Lines 57–64: building the child's genome

```java
int childNumber = CHILDREN + 1;
String childId = ID + "." + childNumber;
String childGenome = genome;
childGenome = setString(childGenome, "ID", childId);
childGenome = setString(childGenome, "PARENT_ID", ID);
childGenome = setInt(childGenome, "GENERATION", GENERATION + 1);
childGenome = setInt(childGenome, "CHILDREN", 0);
```

- **Line 58** sets the child's number to one more than the number of children so far.
- **Line 59** builds the child's ID. For organism `0.1` having its second child, this gives `"0.1" + "." + 2`, which is `"0.1.2"`.
- **Line 60** starts the child's genome as a copy of the parent's text. Java strings can never be modified, so the following lines each produce a *new* string and store it back in `childGenome`.
- **Lines 61–64** rewrite the four genes using `setString` and `setInt` (lines 102–111):
  - the child's `ID` becomes its new ID;
  - its `PARENT_ID` becomes the current organism's ID;
  - its `GENERATION` is one higher;
  - its `CHILDREN` count starts at zero.

## Lines 66–69: giving birth

```java
Path childDir = Files.createDirectories(WORLD.resolve(childId));
Path childFile = childDir.resolve("Organism.java");
Files.writeString(childFile, childGenome);
```

- **`Files.createDirectories(path)`** creates the folder `world/0.1.2`, along with any missing parent folders such as `world` itself. It doesn't complain if the folder already exists. It returns the same path, which is stored in `childDir`.
- **Line 68** builds the path `world/0.1.2/Organism.java`.
- **`Files.writeString(path, text)`** writes the text to the file. With no extra options it creates the file if it doesn't exist and replaces its contents if it does. The child now exists on disk.

## Lines 71–76: the viability check

```java
Boolean viable = compiles(childFile);
if (Boolean.FALSE.equals(viable)) {
    Files.deleteIfExists(childFile);
    Files.deleteIfExists(childDir);
}
```

- **`compiles`** (line 123) tries to compile the child.
- `Boolean`, with a capital B, is the object form of `boolean`. Unlike `boolean`, it can also be `null`. The program uses this to represent three states: `true` (compiled), `false` (failed) and `null` (no compiler available, so unknown).
- **`Boolean.FALSE.equals(viable)`** is true only if `viable` is `false`. Writing it this way round is safe when `viable` is `null`. `viable.equals(...)` would crash in that case, because you can't call a method on `null`.
- **`Files.deleteIfExists(path)`** deletes a file or folder if it exists, and does nothing otherwise. A folder can only be deleted when it's empty, which is why the file is deleted first.

## Line 79: recording the birth

```java
recordBirth(childId, viable);
```

This calls `recordBirth` (line 137), which appends a line to `lineage.csv`. Stillborn births are recorded too.

## Line 82: ageing

```java
Files.writeString(self, setInt(genome, "CHILDREN", childNumber));
```

This takes the parent's **original** genome, changes only its `CHILDREN` gene to the new count, and overwrites the parent's own source file with the result.

This is safe while the program is running. The source launcher compiled the file into memory before `main` started, so changing the file on disk only affects the *next* run.

## Lines 84–90: reporting

```java
String status = viable == null ? "viability unknown (no compiler found)"
              : viable ? "viable" : "stillborn";
System.out.printf("Organism %s (generation %d) gave birth to %s [%s]%n",
        ID, GENERATION, childId, status);
if (Boolean.TRUE.equals(viable)) {
    System.out.println("  Run it with: java " + childFile);
}
```

- **Lines 84–85** use the conditional operator, `condition ? valueIfTrue : valueIfFalse`, twice in a row. Read it as: if `viable` is null, use "unknown"; otherwise, if it's true, use "viable"; otherwise use "stillborn".
- **`System.out.printf(format, values...)`** prints formatted text. In the format string:
  - each `%s` is replaced by the next value as text;
  - `%d` is replaced by a whole number;
  - `%n` is a line break appropriate to the operating system.
- **Lines 88–90**: if the child is viable, the program tells you how to run it. Printing a `Path` prints the path as text.

## Lines 93–99: `locateSelf`, finding its own source file

```java
static Path locateSelf(String[] args) {
    String launched = System.getProperty("jdk.launcher.sourcefile");
    if (launched != null) return Path.of(launched);
    if (args.length > 0) return Path.of(args[0]);
    return Path.of("Organism.java");
}
```

- **`System.getProperty(name)`** reads a "system property", a named setting the JVM holds. When you run `java SomeFile.java`, the launcher stores the source file's path in the property `jdk.launcher.sourcefile`. If the program wasn't started that way, the property doesn't exist and the call returns `null`.
- The method has two fallbacks:
  - **Line 97** uses the first command-line argument as the path.
  - **Line 98** assumes `Organism.java` in the current folder.
- `return` hands a value back to the caller and ends the method immediately. So only one of these lines takes effect.

## Lines 101–105: `setString`, rewriting a text gene

```java
static String setString(String src, String name, String value) {
    Pattern p = Pattern.compile("(?m)^(\\s*static final String " + name + " = )\"[^\"]*\";");
    return replaceGene(src, name, p, "\"" + value + "\";");
}
```

**`Pattern.compile(text)`** turns a regular expression into a reusable `Pattern` object. A regular expression is a mini-language for describing text to search for.

With `name` set to `"ID"`, the pattern reads:

```
(?m)^(\s*static final String ID = )"[^"]*";
```

Piece by piece:

- `(?m)` turns on multi-line mode, so `^` means "start of any line" rather than "start of the whole text".
- `^` matches only at the start of a line. This stops the pattern from matching gene names that appear in the middle of other code.
- `( ... )` is a **capturing group**. It remembers the text it matched, so that part can be kept unchanged later. Here it captures `    static final String ID = `.
- `\s*` matches zero or more whitespace characters (the indentation). In the Java source it's written `\\s` because a backslash inside a Java string literal has to be doubled.
- `static final String ID = ` matches that literal text. Because it must be preceded by `String ` and the start of the line, it can't accidentally match `PARENT_ID`.
- `"` matches a quote mark. In Java source it's written `\"`, because a bare quote would end the string literal.
- `[^"]*` matches any number of characters that aren't quote marks. This is the old value. `[^...]` means "any character except these".
- `";` matches the closing quote and semicolon.

Line 104 builds the replacement value, the new value wrapped in quotes with a semicolon (for example `"0.1.2";`), and passes everything to `replaceGene`.

## Lines 107–111: `setInt`, rewriting a number gene

```java
Pattern p = Pattern.compile("(?m)^(\\s*static final int " + name + " = )-?\\d+;");
return replaceGene(src, name, p, value + ";");
```

This works the same way, but for `int` genes. The value part of the pattern is different:

- `-?` matches an optional minus sign. `?` means "zero or one of the previous thing".
- `\d+` matches one or more digits. `\d` is any digit and `+` means "one or more".

Line 110 builds the replacement. `value + ";"` turns the number into text, for example `2;`.

## Lines 113–120: `replaceGene`, the surgery

```java
static String replaceGene(String src, String name, Pattern p, String newValue) {
    Matcher m = p.matcher(src);
    if (!m.find()) throw new IllegalStateException("Genome damaged: gene " + name + " not found");
    int start = m.start(), end = m.end();
    String prefix = m.group(1);
    if (m.find()) throw new IllegalStateException("Genome damaged: gene " + name + " duplicated");
    return src.substring(0, start) + prefix + newValue + src.substring(end);
}
```

- **`p.matcher(src)`** creates a `Matcher` that will search the genome text with the pattern.
- **`m.find()`** searches forward for the next match. It returns `true` if it found one and `false` otherwise, and it remembers its position, so the next `find()` carries on from there.
- **Line 115** handles the case where no match is found. `!` means "not". The method **throws** an `IllegalStateException`, which stops the program with an error message, so a damaged genome is never copied.
- **`m.start()`** and **`m.end()`** give the character positions where the match begins, and where it ends plus one. For example, a match covering characters 40 to 69 gives start 40 and end 70.
- **`m.group(1)`** returns the text matched by the first capturing group: the `static final String ID = ` part, which is kept as is.
- **Line 118** calls `find()` again. If it finds a *second* match, the gene appears twice, so the genome is ambiguous and the program refuses to continue.
- **Line 119** rebuilds the text from three parts. **`substring(a, b)`** returns the characters from position `a` up to but not including `b`, and **`substring(a)`** returns everything from `a` to the end. So the result is everything before the gene line, the kept prefix, the new value, and everything after the gene line.

## Lines 122–135: `compiles`, the viability check

```java
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
```

- **`ToolProvider.getSystemJavaCompiler()`** returns the compiler built into the JDK. If Java was installed as a runtime only (a JRE) there is no compiler, and it returns `null`. Line 125 then returns `null`, meaning "unknown".
- **`Files.createTempDirectory(prefix)`** creates a new empty folder in the operating system's temporary area, named with the prefix plus a random suffix (for example `organism-check12345`), and returns its path. Compiling produces `.class` files, and this keeps them out of the `world` folder.
- **`try { ... } finally { ... }`**: the `finally` block always runs once the `try` block ends, even if it returns or an error occurs. This guarantees the temporary folder is cleaned up.
- **`javac.run(in, out, err, arguments...)`** runs the compiler exactly as if you had typed `javac` at the command line:
  - The first three parameters are where the compiler reads input, writes normal output, and writes errors. `null` means "use the defaults".
  - `System.err` is the console's error stream, so compile errors in a stillborn child appear on screen.
  - The remaining strings are the command-line arguments: `-d <folder>` means "put the compiled `.class` files in this folder", followed by the source file to compile.
  - **`.toString()`** converts a `Path` to plain text, because this method expects strings.
  - It returns `0` on success and something else on failure.
- **Line 129** returns `true` if the result was 0 and `false` otherwise.
- **Line 131** is a **try-with-resources** statement. The thing created in the brackets is closed automatically when the block ends, which matters because the next call keeps the folder open while it's being read.
  - **`var`** asks Java to work out the variable's type itself. Here the type is `Stream<Path>`.
- **`Files.walk(folder)`** returns a **stream**, a sequence you can process step by step, of every path inside the folder, including the folder itself and anything nested. A folder always comes before its contents.
- **Line 132** processes that stream:
  - **`.sorted(Comparator.reverseOrder())`** sorts the paths in reverse alphabetical order. A file inside a folder has a longer path that starts with the folder's path, so it sorts after the folder, and reversing puts it first. This means contents are deleted before the folders that hold them, which is necessary because only empty folders can be deleted.
  - **`.forEach(f -> ...)`** runs some code for every path. `f -> f.toFile().delete()` is a **lambda**: a small unnamed function that receives each path as `f`.
  - **`f.toFile()`** converts the modern `Path` to the older `java.io.File` type.
  - **`.delete()`** deletes it. Unlike `Files.delete`, it doesn't throw an error if something goes wrong. It just returns false, which is acceptable for clean-up.

## Lines 137–146: `recordBirth`, the lineage log

```java
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
```

- **Line 138** makes sure the `world` folder exists.
- **`Files.exists(path)`** returns whether the file exists. If the log doesn't exist yet, line 140 creates it with a header row naming the columns. `\n` is a newline character.
- **`LocalDateTime.now()`** gets the current date and time from the computer's clock.
- **`.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)`** turns it into text in the standard ISO form, for example `2026-10-23T12:02:51.089`.
- **`String.join(",", a, b, c, ...)`** joins the strings with a comma between each, which is how CSV (comma-separated values) files work.
- **`String.valueOf(x)`** converts a value to text. For a `null` `Boolean` it produces the word `"null"`, so an unknown viability is recorded as `null`.
- **`Files.writeString(path, text, StandardOpenOption.APPEND)`** adds the text to the **end** of the file instead of replacing it. With only `APPEND` given, the file must already exist, which line 140 guarantees.

## Lines 148–151: `population`

```java
static int population() throws IOException {
    if (!Files.exists(LINEAGE)) return 0;
    return Math.max(0, Files.readAllLines(LINEAGE).size() - 1);
}
```

- If there is no log, nobody has been born yet, so the method returns 0.
- **`Files.readAllLines(path)`** reads the file and returns a `List<String>` with one entry per line.
- **`.size()`** is the number of lines. Subtracting 1 excludes the header row.
- **`Math.max(a, b)`** returns the larger of the two numbers. It guards against an empty file giving −1.

## Lines 153–168: `printTree`

```java
Map<String, List<String>> children = new TreeMap<>();
Map<String, String> status = new HashMap<>();
for (String line : Files.readAllLines(LINEAGE).subList(1, population() + 1)) {
    String[] f = line.split(",");
    children.computeIfAbsent(f[1], k -> new ArrayList<>()).add(f[0]);
    status.put(f[0], f[4]);
}
System.out.println("0 (founder)");
printBranch("0", "", children, status);
```

- Lines 155–158 handle the case where there is no log: the method says so and returns.
- A **`Map`** stores key/value pairs, like a dictionary. You look a value up by its key.
- **`children`** maps each parent ID to a **`List`** (an ordered, growable sequence) of its children's IDs.
  - **`TreeMap`** is a map that keeps its keys sorted.
  - `<>` tells Java to infer the types from the left-hand side.
- **`status`** maps each child ID to its viability text. **`HashMap`** is a fast map with no particular order.
- **Line 161** is a for-each loop: it runs the body once for each line.
  - **`.subList(1, n)`** returns lines 1 to n−1 of the list (counting from 0), which skips line 0, the header.
- **`line.split(",")`** cuts the line at each comma and returns the pieces as an array. For example, `"0.1.1,0.1,2,...,true"` gives:
  - `f[0]` = child ID
  - `f[1]` = parent ID
  - `f[2]` = generation
  - `f[3]` = birth time
  - `f[4]` = viable
- **`children.computeIfAbsent(key, k -> new ArrayList<>())`** looks up the parent's list. If the parent has no list yet, it creates an empty **`ArrayList`** (the standard resizable list), stores it, and returns it. **`.add(f[0])`** then appends the child's ID to that list.
- **`status.put(key, value)`** stores the child's viability.
- Line 166 prints the root, and line 167 starts drawing the tree from the founder.

## Lines 170–180: `printBranch`, drawing the tree

```java
List<String> kids = children.getOrDefault(id, List.of());
for (int i = 0; i < kids.size(); i++) {
    boolean last = i == kids.size() - 1;
    String kid = kids.get(i);
    String mark = "false".equals(status.get(kid)) ? "  (stillborn)" : "";
    System.out.println(indent + (last ? "`-- " : "|-- ") + kid + mark);
    printBranch(kid, indent + (last ? "    " : "|   "), children, status);
}
```

- **`getOrDefault(key, fallback)`** returns the value for the key, or the fallback if the key isn't present. **`List.of()`** creates an empty list. So an organism with no children gets an empty list, and the loop simply doesn't run.
- **Line 173** is a counting loop: `i` goes 0, 1, 2, and so on, while it is less than the number of children.
- **Line 174** works out whether this is the last child. It's needed to choose the right branch symbol.
- **`kids.get(i)`** returns the item at position `i` in the list.
- **`status.get(kid)`** looks up the child's viability, which is `null` if absent. If it is `"false"`, the line gets a "(stillborn)" label. As before, putting the known string first keeps the comparison safe when the value is `null`.
- **Line 177** prints the indent, a branch symbol (`` `-- `` for the last child, `|-- ` otherwise), the ID, and the label.
- **Line 178** is **recursion**: the method calls itself for the child, so the child's own children are printed beneath it with a deeper indent. Under a non-last child, the indent includes a `|` so the vertical line continues down to that child's later siblings. Recursion stops naturally when it reaches an organism with no children.

The closing braces on lines 180 and 181 end the method and the class.
