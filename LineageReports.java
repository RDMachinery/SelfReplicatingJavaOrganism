import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import javax.swing.tree.*;

/**
 * LineageReports - a Swing report viewer for the lineage.csv file written by Organism.java.
 *
 * Usage (Java 17+):
 *   java LineageReports.java                          open world/lineage.csv
 *   java LineageReports.java path/to/lineage.csv      open a specific file
 *   java LineageReports.java --summary [file]         print the summary to the console
 *   java LineageReports.java --export <dir> [file]    save every report as a PNG image
 *
 * Reports: Summary, Family Tree, Generations (table and chart), Timeline.
 */
public class LineageReports {

    static final Path DEFAULT_FILE = Path.of("world", "lineage.csv");

    // Chart colours
    static final Color VIABLE = new Color(0x2E7D5B);
    static final Color STILLBORN = new Color(0xC0392B);
    static final Color UNKNOWN = new Color(0x9E9E9E);
    static final Color LINE = new Color(0x2C5F9E);
    static final Color GRID = new Color(0xE3E3E3);
    static final Color AXIS_TEXT = new Color(0x555555);

    static final DateTimeFormatter SHOW_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss");

    // =====================================================================
    // Data model
    // =====================================================================

    /** One row of lineage.csv. viable is null when no compiler was available. */
    record Birth(String id, String parent, int generation, LocalDateTime born, Boolean viable) {}

    /** All births in a lineage file, with the family structure worked out. */
    static final class Lineage {
        final Path file;
        final List<Birth> births;
        final int skippedLines;
        final Map<String, Birth> byId = new HashMap<>();
        final Map<String, List<String>> children = new HashMap<>();
        final List<String> roots = new ArrayList<>();

        Lineage(Path file, List<Birth> births, int skippedLines) {
            this.file = file;
            this.births = births;
            this.skippedLines = skippedLines;
            for (Birth b : births) {
                byId.put(b.id(), b);
                children.computeIfAbsent(b.parent(), k -> new ArrayList<>()).add(b.id());
            }
            children.values().forEach(list -> list.sort(ID_ORDER));
            // A root is a parent that was never itself born in the log, normally the founder "0".
            for (String parent : children.keySet()) {
                if (!byId.containsKey(parent)) roots.add(parent);
            }
            roots.sort(ID_ORDER);
        }

        static Lineage load(Path file) throws IOException {
            List<Birth> births = new ArrayList<>();
            int skipped = 0;
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty() || (i == 0 && line.startsWith("id,"))) continue;
                String[] f = line.split(",", -1);
                try {
                    if (f.length < 5) throw new IllegalArgumentException();
                    Boolean viable = switch (f[4].trim()) {
                        case "true" -> Boolean.TRUE;
                        case "false" -> Boolean.FALSE;
                        default -> null;
                    };
                    births.add(new Birth(f[0].trim(), f[1].trim(), Integer.parseInt(f[2].trim()),
                            LocalDateTime.parse(f[3].trim()), viable));
                } catch (RuntimeException e) {
                    skipped++;
                }
            }
            return new Lineage(file, births, skipped);
        }

        static Lineage empty() {
            return new Lineage(null, List.of(), 0);
        }

        List<String> childrenOf(String id) {
            return children.getOrDefault(id, List.of());
        }

        /** Size of the whole subtree below an organism (children, grandchildren, ...). */
        int descendants(String id) {
            int count = 0;
            Deque<String> todo = new ArrayDeque<>(childrenOf(id));
            while (!todo.isEmpty()) {
                String next = todo.pop();
                count++;
                todo.addAll(childrenOf(next));
            }
            return count;
        }

        /** The chain of IDs from the founder down to this organism, read from the ID itself. */
        String ancestry(String id) {
            String[] parts = id.split("\\.");
            StringBuilder chain = new StringBuilder();
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) { current.append('.'); chain.append("  ->  "); }
                current.append(parts[i]);
                chain.append(current);
            }
            return chain.toString();
        }

        int maxGeneration() {
            return births.stream().mapToInt(Birth::generation).max().orElse(0);
        }
    }

    /** Orders hierarchical IDs numerically, so 0.2 comes before 0.10. */
    static final Comparator<String> ID_ORDER = (a, b) -> {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.min(x.length, y.length); i++) {
            int c;
            try {
                c = Long.compare(Long.parseLong(x[i]), Long.parseLong(y[i]));
            } catch (NumberFormatException e) {
                c = x[i].compareTo(y[i]);
            }
            if (c != 0) return c;
        }
        return Integer.compare(x.length, y.length);
    };

    // =====================================================================
    // Summary figures (shared by the GUI and the --summary option)
    // =====================================================================

    /** One figure in the summary: a caption, a value and an optional detail line. */
    record Figure(String caption, String value, String detail) {}

    static List<Figure> summarise(Lineage l) {
        List<Figure> out = new ArrayList<>();
        int total = l.births.size();
        long viable = l.births.stream().filter(b -> Boolean.TRUE.equals(b.viable())).count();
        long stillborn = l.births.stream().filter(b -> Boolean.FALSE.equals(b.viable())).count();
        long unknown = total - viable - stillborn;

        out.add(new Figure("Total births", String.valueOf(total), "recorded in lineage.csv"));
        out.add(new Figure("Viable", String.valueOf(viable), percent(viable, total)));
        out.add(new Figure("Stillborn", String.valueOf(stillborn), percent(stillborn, total)));
        out.add(new Figure("Viability unknown", String.valueOf(unknown), "born without a compiler check"));
        out.add(new Figure("Generations", String.valueOf(l.maxGeneration()), "below the founder"));

        if (l.roots.size() == 1) {
            String root = l.roots.get(0);
            out.add(new Figure("Founder's children", String.valueOf(l.childrenOf(root).size()),
                    "direct offspring of " + root));
        } else {
            out.add(new Figure("Founders", String.valueOf(l.roots.size()), "separate family trees"));
        }

        Optional<Birth> deepest = l.births.stream().max(Comparator.comparingInt(Birth::generation)
                .thenComparing(Birth::id, ID_ORDER.reversed()));
        out.add(new Figure("Deepest lineage", deepest.map(Birth::id).orElse("-"),
                deepest.map(b -> "generation " + b.generation()).orElse("")));

        Optional<String> prolific = l.children.keySet().stream()
                .max(Comparator.comparingInt((String p) -> l.childrenOf(p).size())
                        .thenComparing(ID_ORDER.reversed()));
        out.add(new Figure("Most prolific", prolific.orElse("-"),
                prolific.map(p -> plural(l.childrenOf(p).size(), "child", "children")).orElse("")));

        int parents = l.children.size();
        out.add(new Figure("Children per parent",
                parents == 0 ? "-" : String.format(Locale.UK, "%.2f", (double) total / parents),
                "average across " + plural(parents, "parent", "parents")));

        long childless = l.births.stream()
                .filter(b -> !Boolean.FALSE.equals(b.viable()) && l.childrenOf(b.id()).isEmpty())
                .count();
        out.add(new Figure("Yet to reproduce", String.valueOf(childless), "living organisms with no children"));

        Optional<LocalDateTime> first = l.births.stream().map(Birth::born).min(Comparator.naturalOrder());
        Optional<LocalDateTime> last = l.births.stream().map(Birth::born).max(Comparator.naturalOrder());
        out.add(new Figure("First birth", first.map(t -> t.format(SHOW_TIME)).orElse("-"), ""));
        out.add(new Figure("Latest birth", last.map(t -> t.format(SHOW_TIME)).orElse("-"), ""));
        out.add(new Figure("Experiment span",
                first.isPresent() ? describe(Duration.between(first.get(), last.get())) : "-",
                "from first to latest birth"));
        return out;
    }

    static String percent(long part, long whole) {
        return whole == 0 ? "" : String.format(Locale.UK, "%.1f%% of births", 100.0 * part / whole);
    }

    static String plural(long n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    static String describe(Duration d) {
        long s = d.getSeconds();
        long days = s / 86400, hours = s % 86400 / 3600, mins = s % 3600 / 60, secs = s % 60;
        if (days > 0) return days + "d " + hours + "h " + mins + "m";
        if (hours > 0) return hours + "h " + mins + "m " + secs + "s";
        if (mins > 0) return mins + "m " + secs + "s";
        return secs + "s";
    }

    // =====================================================================
    // Entry point
    // =====================================================================

    public static void main(String[] args) throws Exception {
        List<String> rest = new ArrayList<>(Arrays.asList(args));

        if (!rest.isEmpty() && rest.get(0).equals("--summary")) {
            Path file = rest.size() > 1 ? Path.of(rest.get(1)) : DEFAULT_FILE;
            requireFile(file);
            printSummary(Lineage.load(file));
            return;
        }

        if (!rest.isEmpty() && rest.get(0).equals("--export")) {
            if (rest.size() < 2) {
                System.err.println("Usage: java LineageReports.java --export <folder> [lineage.csv]");
                System.exit(1);
            }
            Path dir = Path.of(rest.get(1));
            Path file = rest.size() > 2 ? Path.of(rest.get(2)) : DEFAULT_FILE;
            requireFile(file);
            exportAll(Lineage.load(file), dir);
            return;
        }

        Path file = rest.isEmpty() ? DEFAULT_FILE : Path.of(rest.get(0));
        SwingUtilities.invokeLater(() -> new ReportWindow(file).setVisible(true));
    }

    static void requireFile(Path file) {
        if (!Files.exists(file)) {
            System.err.println("No lineage file found at " + file.toAbsolutePath());
            System.err.println("Run an organism first, or give the path to lineage.csv.");
            System.exit(1);
        }
    }

    static void printSummary(Lineage l) {
        System.out.println("Lineage summary: " + l.file);
        for (Figure f : summarise(l)) {
            System.out.printf("  %-22s %-24s %s%n", f.caption(), f.value(), f.detail());
        }
        if (l.skippedLines > 0) {
            System.out.println("  (" + plural(l.skippedLines, "line", "lines") + " could not be read and were skipped)");
        }
    }

    /** Saves every report as a PNG, without showing a window. Needs a desktop session. */
    static void exportAll(Lineage l, Path dir) throws Exception {
        Files.createDirectories(dir);
        Map<String, JComponent> reports = buildReports(l);
        JFrame offscreen = new JFrame();
        JTabbedPane tabs = new JTabbedPane();
        reports.forEach(tabs::addTab);
        offscreen.add(tabs);
        offscreen.setSize(1000, 680);
        offscreen.addNotify();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            tabs.setSelectedIndex(i);
            offscreen.validate();
            String name = tabs.getTitleAt(i).toLowerCase(Locale.UK).replace(' ', '-');
            Path out = dir.resolve(name + ".png");
            saveImage(tabs.getComponentAt(i), out.toFile());
            System.out.println("Saved " + out);
        }
        offscreen.dispose();
    }

    static void saveImage(Component c, File out) throws IOException {
        BufferedImage img = new BufferedImage(Math.max(1, c.getWidth()), Math.max(1, c.getHeight()),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        c.printAll(g);
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    /** The four reports, in tab order. */
    static Map<String, JComponent> buildReports(Lineage l) {
        Map<String, JComponent> reports = new LinkedHashMap<>();
        reports.put("Summary", new SummaryPanel(l));
        reports.put("Family Tree", new TreePanel(l));
        reports.put("Generations", new GenerationsPanel(l));
        reports.put("Timeline", new TimelineChart(l));
        return reports;
    }

    // =====================================================================
    // Main window
    // =====================================================================

    static final class ReportWindow extends JFrame {
        private final JTabbedPane tabs = new JTabbedPane();
        private Path file;

        ReportWindow(Path file) {
            super("Lineage Reports");
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setJMenuBar(buildMenu());
            add(tabs);
            setSize(1000, 680);
            setLocationRelativeTo(null);
            load(file);
        }

        private JMenuBar buildMenu() {
            JMenu menu = new JMenu("File");
            menu.add(item("Open...", e -> chooseFile()));
            menu.add(item("Reload", e -> load(file)));
            menu.addSeparator();
            menu.add(item("Export current report as PNG...", e -> exportCurrent()));
            menu.addSeparator();
            menu.add(item("Exit", e -> dispose()));
            JMenuBar bar = new JMenuBar();
            bar.add(menu);
            return bar;
        }

        private static JMenuItem item(String text, java.awt.event.ActionListener action) {
            JMenuItem item = new JMenuItem(text);
            item.addActionListener(action);
            return item;
        }

        private void load(Path path) {
            Lineage lineage;
            if (path != null && Files.exists(path)) {
                try {
                    lineage = Lineage.load(path);
                    file = path;
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(this, "Could not read " + path + ":\n" + e.getMessage(),
                            "Lineage Reports", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } else {
                lineage = Lineage.empty();
                file = path;
            }
            int selected = Math.max(0, tabs.getSelectedIndex());
            tabs.removeAll();
            buildReports(lineage).forEach(tabs::addTab);
            tabs.setSelectedIndex(Math.min(selected, tabs.getTabCount() - 1));
            setTitle("Lineage Reports - " + (lineage.file != null ? lineage.file : "no file loaded"));
            if (lineage.skippedLines > 0) {
                JOptionPane.showMessageDialog(this,
                        plural(lineage.skippedLines, "line", "lines") + " could not be read and were skipped.",
                        "Lineage Reports", JOptionPane.WARNING_MESSAGE);
            }
        }

        private void chooseFile() {
            JFileChooser chooser = new JFileChooser(file != null && file.getParent() != null
                    ? file.getParent().toFile() : new File("."));
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV files", "csv"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                load(chooser.getSelectedFile().toPath());
            }
        }

        private void exportCurrent() {
            Component report = tabs.getSelectedComponent();
            String name = tabs.getTitleAt(tabs.getSelectedIndex()).toLowerCase(Locale.UK).replace(' ', '-');
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File(name + ".png"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    saveImage(report, chooser.getSelectedFile());
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(this, "Could not save image:\n" + e.getMessage(),
                            "Lineage Reports", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    // =====================================================================
    // Report 1: Summary
    // =====================================================================

    static final class SummaryPanel extends JPanel {
        SummaryPanel(Lineage l) {
            super(new BorderLayout());
            setBackground(Color.WHITE);
            setBorder(new EmptyBorder(20, 24, 20, 24));

            JLabel title = new JLabel("Experiment summary");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
            JLabel source = new JLabel(l.file == null
                    ? "No lineage file loaded. Use File > Open to choose one."
                    : "Source: " + l.file.toAbsolutePath());
            source.setForeground(AXIS_TEXT);
            JPanel header = new JPanel(new GridLayout(2, 1, 0, 4));
            header.setOpaque(false);
            header.setBorder(new EmptyBorder(0, 0, 16, 0));
            header.add(title);
            header.add(source);
            add(header, BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridLayout(0, 4, 12, 12));
            grid.setOpaque(false);
            for (Figure f : summarise(l)) grid.add(card(f));
            JPanel holder = new JPanel(new BorderLayout());
            holder.setOpaque(false);
            holder.add(grid, BorderLayout.NORTH);
            add(holder, BorderLayout.CENTER);
        }

        private static JPanel card(Figure f) {
            JPanel card = new JPanel(new BorderLayout(0, 4));
            card.setBackground(new Color(0xF6F7F9));
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0xDDE1E6)), new EmptyBorder(10, 12, 10, 12)));
            JLabel caption = new JLabel(f.caption());
            caption.setForeground(AXIS_TEXT);
            JLabel value = new JLabel(f.value());
            value.setFont(value.getFont().deriveFont(Font.BOLD, f.value().length() > 12 ? 14f : 22f));
            JLabel detail = new JLabel(f.detail().isEmpty() ? " " : f.detail());
            detail.setForeground(AXIS_TEXT);
            detail.setFont(detail.getFont().deriveFont(11f));
            card.add(caption, BorderLayout.NORTH);
            card.add(value, BorderLayout.CENTER);
            card.add(detail, BorderLayout.SOUTH);
            return card;
        }
    }

    // =====================================================================
    // Report 2: Family tree
    // =====================================================================

    static final class TreePanel extends JPanel {
        private final Lineage lineage;
        private final JTree tree;
        private final JTextArea details = new JTextArea();

        TreePanel(Lineage l) {
            super(new BorderLayout());
            this.lineage = l;

            DefaultMutableTreeNode top = new DefaultMutableTreeNode("All organisms");
            for (String root : l.roots) top.add(buildNode(root));
            tree = new JTree(top);
            tree.setRootVisible(false);
            tree.setShowsRootHandles(true);
            tree.setCellRenderer(new OrganismRenderer());
            tree.addTreeSelectionListener(e -> showDetails());
            expandAll(true);

            details.setEditable(false);
            details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            details.setBorder(new EmptyBorder(12, 12, 12, 12));
            details.setText(l.births.isEmpty() ? "No births recorded." : "Select an organism to see its details.");

            JButton expand = new JButton("Expand all");
            expand.addActionListener(e -> expandAll(true));
            JButton collapse = new JButton("Collapse all");
            collapse.addActionListener(e -> expandAll(false));
            JLabel key = new JLabel("   Stillborn organisms are shown in red; unknown viability in grey.");
            key.setForeground(AXIS_TEXT);
            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
            toolbar.add(expand);
            toolbar.add(collapse);
            toolbar.add(key);

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    new JScrollPane(tree), new JScrollPane(details));
            split.setResizeWeight(0.55);
            split.setDividerLocation(540);
            add(toolbar, BorderLayout.NORTH);
            add(split, BorderLayout.CENTER);
        }

        private DefaultMutableTreeNode buildNode(String id) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(id);
            for (String child : lineage.childrenOf(id)) node.add(buildNode(child));
            return node;
        }

        private void expandAll(boolean expand) {
            if (expand) {
                for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
            } else {
                for (int i = tree.getRowCount() - 1; i >= 0; i--) tree.collapseRow(i);
            }
        }

        private void showDetails() {
            TreePath path = tree.getSelectionPath();
            if (path == null) return;
            String id = (String) ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
            Birth b = lineage.byId.get(id);
            StringBuilder s = new StringBuilder();
            s.append("Organism     ").append(id).append('\n');
            if (b == null) {
                s.append("Role         founder (not itself recorded as a birth)\n");
            } else {
                s.append("Parent       ").append(b.parent()).append('\n');
                s.append("Generation   ").append(b.generation()).append('\n');
                s.append("Born         ").append(b.born().format(SHOW_TIME)).append('\n');
                s.append("Viability    ").append(b.viable() == null ? "unknown"
                        : b.viable() ? "viable" : "stillborn").append('\n');
            }
            s.append("Children     ").append(lineage.childrenOf(id).size()).append('\n');
            s.append("Descendants  ").append(lineage.descendants(id)).append('\n');
            s.append('\n').append("Ancestry\n  ").append(lineage.ancestry(id)).append('\n');
            List<String> kids = lineage.childrenOf(id);
            if (!kids.isEmpty()) {
                s.append('\n').append("Children\n");
                for (String k : kids) s.append("  ").append(k).append('\n');
            }
            details.setText(s.toString());
            details.setCaretPosition(0);
        }

        private final class OrganismRenderer extends DefaultTreeCellRenderer {
            @Override
            public Component getTreeCellRendererComponent(JTree t, Object value, boolean sel, boolean expanded,
                                                          boolean leaf, int row, boolean focus) {
                super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, focus);
                Object user = ((DefaultMutableTreeNode) value).getUserObject();
                if (!(user instanceof String id)) return this;
                Birth b = lineage.byId.get(id);
                int n = lineage.childrenOf(id).size();
                String suffix = b == null ? "  (founder)" : n > 0 ? "  (" + plural(n, "child", "children") + ")" : "";
                if (b != null && Boolean.FALSE.equals(b.viable())) suffix = "  (stillborn)";
                setText(id + suffix);
                if (!sel && b != null) {
                    if (Boolean.FALSE.equals(b.viable())) setForeground(STILLBORN);
                    else if (b.viable() == null) setForeground(UNKNOWN);
                }
                return this;
            }
        }
    }

    // =====================================================================
    // Report 3: Generations
    // =====================================================================

    /** Per-generation counts, shared by the table and the chart. */
    record GenerationRow(int generation, int births, int viable, int stillborn, int unknown, int parents) {}

    static List<GenerationRow> generationRows(Lineage l) {
        List<GenerationRow> rows = new ArrayList<>();
        for (int g = 1; g <= l.maxGeneration(); g++) {
            final int gen = g;
            List<Birth> inGen = l.births.stream().filter(b -> b.generation() == gen).toList();
            int viable = (int) inGen.stream().filter(b -> Boolean.TRUE.equals(b.viable())).count();
            int stillborn = (int) inGen.stream().filter(b -> Boolean.FALSE.equals(b.viable())).count();
            int parents = (int) inGen.stream().map(Birth::parent).distinct().count();
            rows.add(new GenerationRow(g, inGen.size(), viable, stillborn,
                    inGen.size() - viable - stillborn, parents));
        }
        return rows;
    }

    static final class GenerationsPanel extends JPanel {
        GenerationsPanel(Lineage l) {
            super(new BorderLayout());
            List<GenerationRow> rows = generationRows(l);

            String[] columns = {"Generation", "Births", "Viable", "Stillborn", "Unknown",
                    "Parents", "Children per parent", "Stillbirth rate"};
            DefaultTableModel model = new DefaultTableModel(columns, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
                @Override public Class<?> getColumnClass(int c) {
                    return c <= 5 ? Integer.class : Double.class;
                }
            };
            for (GenerationRow r : rows) {
                model.addRow(new Object[]{r.generation(), r.births(), r.viable(), r.stillborn(), r.unknown(),
                        r.parents(), r.parents() == 0 ? 0.0 : (double) r.births() / r.parents(),
                        r.births() == 0 ? 0.0 : 100.0 * r.stillborn() / r.births()});
            }
            JTable table = new JTable(model);
            table.setAutoCreateRowSorter(true);
            table.setFillsViewportHeight(true);
            DefaultTableCellRenderer twoPlaces = new DefaultTableCellRenderer() {
                @Override protected void setValue(Object v) {
                    setText(v == null ? "" : String.format(Locale.UK, "%.2f", (Double) v));
                }
            };
            twoPlaces.setHorizontalAlignment(SwingConstants.RIGHT);
            table.getColumnModel().getColumn(6).setCellRenderer(twoPlaces);
            DefaultTableCellRenderer pct = new DefaultTableCellRenderer() {
                @Override protected void setValue(Object v) {
                    setText(v == null ? "" : String.format(Locale.UK, "%.1f%%", (Double) v));
                }
            };
            pct.setHorizontalAlignment(SwingConstants.RIGHT);
            table.getColumnModel().getColumn(7).setCellRenderer(pct);

            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                    new JScrollPane(table), new GenerationChart(rows));
            split.setResizeWeight(0.35);
            split.setDividerLocation(200);
            add(split, BorderLayout.CENTER);
        }
    }

    /** Stacked bar chart of births per generation, split by viability. */
    static final class GenerationChart extends JPanel {
        private final List<GenerationRow> rows;

        GenerationChart(List<GenerationRow> rows) {
            this.rows = rows;
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(800, 380));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = prepare(g0);
            drawTitle(g, "Births per generation");
            if (rows.isEmpty()) { drawEmpty(g, this, "No births recorded"); return; }

            int left = 60, right = 30, top = 70, bottom = 50;
            int w = getWidth() - left - right, h = getHeight() - top - bottom;
            int max = rows.stream().mapToInt(GenerationRow::births).max().orElse(1);
            int step = niceStep(max);
            int yMax = (int) Math.ceil((double) max / step) * step;

            drawYAxis(g, left, top, w, h, yMax, step, "Births");
            drawLegend(g, getWidth() - right, 44, new String[]{"Viable", "Stillborn", "Unknown"},
                    new Color[]{VIABLE, STILLBORN, UNKNOWN});

            double slot = (double) w / rows.size();
            int barWidth = (int) Math.max(2, Math.min(60, slot * 0.65));
            int labelEvery = Math.max(1, (int) Math.ceil(36 / slot));
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < rows.size(); i++) {
                GenerationRow r = rows.get(i);
                int x = left + (int) (slot * i + (slot - barWidth) / 2);
                int base = top + h;
                int[] parts = {r.viable(), r.stillborn(), r.unknown()};
                Color[] colours = {VIABLE, STILLBORN, UNKNOWN};
                for (int p = 0; p < parts.length; p++) {
                    int barH = (int) Math.round((double) parts[p] / yMax * h);
                    if (barH == 0) continue;
                    g.setColor(colours[p]);
                    g.fillRect(x, base - barH, barWidth, barH);
                    base -= barH;
                }
                if (slot >= 18) {
                    g.setColor(Color.DARK_GRAY);
                    String count = String.valueOf(r.births());
                    g.drawString(count, x + (barWidth - fm.stringWidth(count)) / 2, base - 4);
                }
                if (i % labelEvery == 0) {
                    g.setColor(AXIS_TEXT);
                    String label = String.valueOf(r.generation());
                    g.drawString(label, x + (barWidth - fm.stringWidth(label)) / 2, top + h + 18);
                }
            }
            g.setColor(AXIS_TEXT);
            String xTitle = "Generation";
            g.drawString(xTitle, left + (w - fm.stringWidth(xTitle)) / 2, top + h + 38);
        }
    }

    // =====================================================================
    // Report 4: Timeline
    // =====================================================================

    /** Cumulative number of births over time. */
    static final class TimelineChart extends JPanel {
        private final List<Birth> sorted;

        TimelineChart(Lineage l) {
            sorted = l.births.stream().sorted(Comparator.comparing(Birth::born)).toList();
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(800, 450));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = prepare(g0);
            drawTitle(g, "Population growth over time");
            if (sorted.isEmpty()) { drawEmpty(g, this, "No births recorded"); return; }

            int left = 60, right = 40, top = 70, bottom = 60;
            int w = getWidth() - left - right, h = getHeight() - top - bottom;
            long t0 = millis(sorted.get(0).born());
            long t1 = millis(sorted.get(sorted.size() - 1).born());
            if (t1 == t0) t1 = t0 + 1000;
            int n = sorted.size();
            int step = niceStep(n);
            int yMax = (int) Math.ceil((double) n / step) * step;

            drawYAxis(g, left, top, w, h, yMax, step, "Total births");

            // Time axis: five evenly spaced labels, formatted to suit the span.
            Duration span = Duration.ofMillis(t1 - t0);
            DateTimeFormatter fmt = span.toDays() >= 1 ? DateTimeFormatter.ofPattern("d MMM HH:mm")
                    : DateTimeFormatter.ofPattern("HH:mm:ss");
            FontMetrics fm = g.getFontMetrics();
            g.setColor(AXIS_TEXT);
            for (int i = 0; i <= 4; i++) {
                long t = t0 + (t1 - t0) * i / 4;
                int x = left + (int) ((double) (t - t0) / (t1 - t0) * w);
                String label = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(fmt);
                g.drawString(label, x - fm.stringWidth(label) / 2, top + h + 18);
            }
            String xTitle = "Time (span " + describe(span) + ")";
            g.drawString(xTitle, left + (w - fm.stringWidth(xTitle)) / 2, top + h + 40);

            // Step line: the count rises by one at each birth.
            Path2D.Double line = new Path2D.Double();
            double prevY = top + h;
            line.moveTo(left, prevY);
            for (int i = 0; i < n; i++) {
                double x = left + (double) (millis(sorted.get(i).born()) - t0) / (t1 - t0) * w;
                double y = top + h - (double) (i + 1) / yMax * h;
                line.lineTo(x, prevY);
                line.lineTo(x, y);
                prevY = y;
            }
            line.lineTo(left + w, prevY);
            g.setColor(LINE);
            g.setStroke(new BasicStroke(2f));
            g.draw(line);

            // Mark each birth, coloured by viability, when there are few enough to see.
            if (n <= 300) {
                for (int i = 0; i < n; i++) {
                    Birth b = sorted.get(i);
                    int x = left + (int) ((double) (millis(b.born()) - t0) / (t1 - t0) * w);
                    int y = top + h - (int) Math.round((double) (i + 1) / yMax * h);
                    g.setColor(b.viable() == null ? UNKNOWN : b.viable() ? VIABLE : STILLBORN);
                    g.fillOval(x - 3, y - 3, 7, 7);
                }
                drawLegend(g, getWidth() - right, 44, new String[]{"Viable", "Stillborn", "Unknown"},
                        new Color[]{VIABLE, STILLBORN, UNKNOWN});
            }
        }

        private static long millis(LocalDateTime t) {
            return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
    }

    // =====================================================================
    // Chart helpers
    // =====================================================================

    static Graphics2D prepare(Graphics g0) {
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    static void drawTitle(Graphics2D g, String title) {
        Font original = g.getFont();
        g.setFont(original.deriveFont(Font.BOLD, 16f));
        g.setColor(Color.DARK_GRAY);
        g.drawString(title, 20, 30);
        g.setFont(original);
    }

    static void drawEmpty(Graphics2D g, JComponent c, String message) {
        g.setColor(AXIS_TEXT);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(message, (c.getWidth() - fm.stringWidth(message)) / 2, c.getHeight() / 2);
    }

    /** Horizontal gridlines with value labels, plus the axis title. */
    static void drawYAxis(Graphics2D g, int left, int top, int w, int h, int yMax, int step, String title) {
        FontMetrics fm = g.getFontMetrics();
        for (int v = 0; v <= yMax; v += step) {
            int y = top + h - (int) Math.round((double) v / yMax * h);
            g.setColor(v == 0 ? Color.GRAY : GRID);
            g.drawLine(left, y, left + w, y);
            g.setColor(AXIS_TEXT);
            String label = String.valueOf(v);
            g.drawString(label, left - 8 - fm.stringWidth(label), y + fm.getAscent() / 2 - 1);
        }
        g.setColor(AXIS_TEXT);
        g.drawString(title, left - 50, top - 14);
    }

    /** A row of coloured swatches, right-aligned to the given x position. */
    static void drawLegend(Graphics2D g, int rightEdge, int y, String[] labels, Color[] colours) {
        FontMetrics fm = g.getFontMetrics();
        int x = rightEdge;
        for (int i = labels.length - 1; i >= 0; i--) {
            x -= fm.stringWidth(labels[i]);
            g.setColor(AXIS_TEXT);
            g.drawString(labels[i], x, y);
            x -= 16;
            g.setColor(colours[i]);
            g.fillRect(x, y - 10, 11, 11);
            x -= 16;
        }
    }

    /** A "nice" gridline interval (1, 2, 5, 10, 20, 50 ...) giving roughly five lines. */
    static int niceStep(int max) {
        double raw = Math.max(1, max) / 5.0;
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        double norm = raw / magnitude;
        double nice = norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 5 ? 5 : 10;
        return Math.max(1, (int) Math.round(nice * magnitude));
    }
}
