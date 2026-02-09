/*
 * Copyright (c) nosqlbench
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/// Parses JaCoCo aggregate CSV reports and prints coverage summaries.
///
/// Usage: `java tools/CoverageReport.java [path-to-jacoco.csv]`
///
/// Defaults to `paramodel-coverage/target/site/jacoco-aggregate/jacoco.csv`
/// if no argument is provided.
public class CoverageReport {

    record Coverage(long instructionMissed, long instructionCovered,
                    long branchMissed, long branchCovered,
                    long lineMissed, long lineCovered,
                    long methodMissed, long methodCovered) {

        Coverage add(Coverage other) {
            return new Coverage(
                instructionMissed + other.instructionMissed,
                instructionCovered + other.instructionCovered,
                branchMissed + other.branchMissed,
                branchCovered + other.branchCovered,
                lineMissed + other.lineMissed,
                lineCovered + other.lineCovered,
                methodMissed + other.methodMissed,
                methodCovered + other.methodCovered
            );
        }

        static Coverage ZERO = new Coverage(0, 0, 0, 0, 0, 0, 0, 0);

        String pct(long covered, long missed) {
            long total = covered + missed;
            return total == 0 ? "N/A" : "%.1f%%".formatted(covered * 100.0 / total);
        }

        String instructionPct() { return pct(instructionCovered, instructionMissed); }
        String branchPct() { return pct(branchCovered, branchMissed); }
        String linePct() { return pct(lineCovered, lineMissed); }
        String methodPct() { return pct(methodCovered, methodMissed); }
    }

    public static void main(String[] args) throws IOException {
        Path csvPath = args.length > 0
            ? Path.of(args[0])
            : Path.of("paramodel-coverage/target/site/jacoco-aggregate/jacoco.csv");

        if (!Files.exists(csvPath)) {
            System.err.println("Coverage CSV not found: " + csvPath);
            System.err.println("Run 'mvn clean verify' first to generate coverage data.");
            System.exit(1);
        }

        var moduleStats = new TreeMap<String, Coverage>();
        var packageStats = new TreeMap<String, Coverage>();

        var lines = Files.readAllLines(csvPath);
        for (int i = 1; i < lines.size(); i++) {
            String[] fields = parseCsvLine(lines.get(i));
            if (fields.length < 13) continue;

            String group = fields[0];
            String pkg = fields[1];
            String module = group.contains("/") ? group.substring(group.lastIndexOf('/') + 1) : group;

            var cov = new Coverage(
                Long.parseLong(fields[3]), Long.parseLong(fields[4]),
                Long.parseLong(fields[5]), Long.parseLong(fields[6]),
                Long.parseLong(fields[7]), Long.parseLong(fields[8]),
                Long.parseLong(fields[11]), Long.parseLong(fields[12])
            );

            moduleStats.merge(module, cov, Coverage::add);
            packageStats.merge(module + "::" + pkg, cov, Coverage::add);
        }

        // Module summary
        printHeader("AGGREGATE COVERAGE BY MODULE");
        System.out.printf("%-25s %15s %10s %10s %10s%n",
            "Module", "Instruction", "Branch", "Line", "Method");
        printDivider(80);

        Coverage grand = Coverage.ZERO;
        for (var entry : moduleStats.entrySet()) {
            var c = entry.getValue();
            System.out.printf("%-25s %15s %10s %10s %10s%n",
                entry.getKey(), c.instructionPct(), c.branchPct(), c.linePct(), c.methodPct());
            grand = grand.add(c);
        }
        printDivider(80);
        System.out.printf("%-25s %15s %10s %10s %10s%n",
            "TOTAL", grand.instructionPct(), grand.branchPct(), grand.linePct(), grand.methodPct());

        // Per-module package breakdowns
        for (String module : moduleStats.keySet()) {
            System.out.println();
            printHeader(module.toUpperCase() + " COVERAGE BY PACKAGE");
            System.out.printf("%-55s %10s %10s%n", "Package", "Line", "Method");
            printDivider(80);

            for (var entry : packageStats.entrySet()) {
                if (entry.getKey().startsWith(module + "::")) {
                    String pkg = entry.getKey().substring(entry.getKey().indexOf("::") + 2);
                    var c = entry.getValue();
                    System.out.printf("%-55s %10s %10s%n", pkg, c.linePct(), c.methodPct());
                }
            }
        }

        // Zero-coverage classes
        System.out.println();
        printHeader("CLASSES WITH ZERO LINE COVERAGE");
        System.out.printf("%-55s %-30s %6s%n", "Module/Package", "Class", "Lines");
        printDivider(95);

        int zeroCount = 0;
        for (int i = 1; i < lines.size(); i++) {
            String[] fields = parseCsvLine(lines.get(i));
            if (fields.length < 13) continue;

            long lineCovered = Long.parseLong(fields[8]);
            long lineMissed = Long.parseLong(fields[7]);
            if (lineCovered == 0 && lineMissed > 0) {
                String group = fields[0];
                String module = group.contains("/") ? group.substring(group.lastIndexOf('/') + 1) : group;
                String pkg = fields[1];
                String cls = fields[2];
                System.out.printf("%-55s %-30s %6d%n", module + "/" + pkg, cls, lineMissed);
                zeroCount++;
            }
        }
        System.out.println();
        System.out.println("Total classes with zero coverage: " + zeroCount);
    }

    private static void printHeader(String title) {
        System.out.println("=".repeat(95));
        System.out.println(title);
        System.out.println("=".repeat(95));
    }

    private static void printDivider(int width) {
        System.out.println("-".repeat(width));
    }

    /// Simple CSV parser that handles quoted fields.
    private static String[] parseCsvLine(String line) {
        var fields = new java.util.ArrayList<String>();
        var current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString().trim());
        return fields.toArray(String[]::new);
    }
}
