package com.sh3d.mcp.command.shape.csg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CSG Boolean operations: union, subtract, intersect.
 * Ported from csg.js BSP-tree algorithm.
 *
 * <p>All operations take two lists of polygons and return a new list of polygons
 * representing the result of the Boolean operation.</p>
 */
final class CsgOperation {

    private CsgOperation() {
    }

    /**
     * Union: A | B — combines both solids into one.
     */
    static List<CsgPolygon> union(List<CsgPolygon> polygonsA, List<CsgPolygon> polygonsB) {
        return union(polygonsA, polygonsB, 0);
    }

    static List<CsgPolygon> union(List<CsgPolygon> polygonsA, List<CsgPolygon> polygonsB, long deadlineMs) {
        CsgNode a = buildTree(polygonsA, deadlineMs);
        CsgNode b = buildTree(polygonsB, deadlineMs);
        a.clipTo(b);
        b.clipTo(a);
        b.invert();
        b.clipTo(a);
        b.invert();
        a.build(b.allPolygons());
        return a.allPolygons();
    }

    /**
     * Subtract: A - B — removes B from A.
     */
    static List<CsgPolygon> subtract(List<CsgPolygon> polygonsA, List<CsgPolygon> polygonsB) {
        return subtract(polygonsA, polygonsB, 0);
    }

    static List<CsgPolygon> subtract(List<CsgPolygon> polygonsA, List<CsgPolygon> polygonsB, long deadlineMs) {
        CsgNode a = buildTree(polygonsA, deadlineMs);
        CsgNode b = buildTree(polygonsB, deadlineMs);
        a.invert();
        a.clipTo(b);
        b.clipTo(a);
        b.invert();
        b.clipTo(a);
        b.invert();
        a.build(b.allPolygons());
        a.invert();
        return a.allPolygons();
    }

    /**
     * Intersect: A & B — keeps only the overlapping volume.
     */
    static List<CsgPolygon> intersect(List<CsgPolygon> polygonsA, List<CsgPolygon> polygonsB) {
        return intersect(polygonsA, polygonsB, 0);
    }

    static List<CsgPolygon> intersect(List<CsgPolygon> polygonsA, List<CsgPolygon> polygonsB, long deadlineMs) {
        CsgNode a = buildTree(polygonsA, deadlineMs);
        CsgNode b = buildTree(polygonsB, deadlineMs);
        a.invert();
        b.clipTo(a);
        b.invert();
        a.clipTo(b);
        b.clipTo(a);
        a.build(b.allPolygons());
        a.invert();
        return a.allPolygons();
    }

    /**
     * Builds a BSP tree from polygons with optional deadline.
     * Shuffles input to randomize splitting plane selection, producing a balanced
     * tree (O(log n) depth) instead of a degenerate linear chain. This is critical
     * for curved geometry (spheres, cylinders) where ordered polygons cause worst-case
     * O(n) depth, making all CSG operations O(n^2).
     */
    private static CsgNode buildTree(List<CsgPolygon> polygons, long deadlineMs) {
        List<CsgPolygon> shuffled = new ArrayList<>(polygons);
        Collections.shuffle(shuffled);
        CsgNode node = new CsgNode();
        node.setDeadline(deadlineMs);
        node.build(shuffled);
        return node;
    }
}
