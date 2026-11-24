# Lineage Reports

`LineageReports.java` is a Swing report viewer for the `world/lineage.csv` file that [`Organism.java`](Organism.java) writes each time an organism gives birth. Like the organism, it is a single source file with no dependencies beyond the JDK.

## Requirements

- Java 17 or later
- A desktop session (for the window and for PNG export)

## Running it

Run it from the same folder you run the organisms from:

```bash
java LineageReports.java                        # open world/lineage.csv
java LineageReports.java path/to/lineage.csv    # open a different lineage file
```

If no lineage file is found, the window opens empty. Use **File > Open** to choose one.

### Without the window

```bash
java LineageReports.java --summary [lineage.csv]           # print the summary to the console
java LineageReports.java --export <folder> [lineage.csv]   # save every report as a PNG
```

`--export` writes `summary.png`, `family-tree.png`, `generations.png` and `timeline.png` to the folder, creating it if necessary.

## The reports

Each report has its own tab.

| Tab | What it shows |
|---|---|
| **Summary** | Headline figures: total births; viable, stillborn and unknown counts; number of generations; the founder's children; the deepest lineage; the most prolific organism; average children per parent; organisms yet to reproduce; and when the experiment started and last changed. |
| **Family Tree** | Every organism in an expandable tree, with its number of children. Stillborn organisms are shown in red and unknown viability in grey. Select an organism to see its details on the right: parent, generation, birth time, viability, children, total descendants, and its ancestry back to the founder. |
| **Generations** | A table with one row per generation (births, viable, stillborn, unknown, number of parents, children per parent and stillbirth rate) above a stacked bar chart of births per generation. Click a column heading to sort by it. |
| **Timeline** | Cumulative population against time, with each birth marked and coloured by viability. Markers are hidden once there are more than 300 births, to keep the chart readable. |

## Menu

- **File > Open…** loads a different `lineage.csv`.
- **File > Reload** re-reads the current file. Use it after running more organisms, so the reports catch up without restarting.
- **File > Export current report as PNG…** saves the visible tab as an image.
- **File > Exit** closes the viewer.

## Notes

- The viewer only reads `lineage.csv`; it never changes it or any organism.
- Lines it can't parse are skipped, and the viewer tells you how many there were.
- The founder (`0`) appears in the tree even though it has no row of its own, because only births are logged.
- IDs are ordered numerically, so `0.2` comes before `0.10`.
