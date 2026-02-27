package com.sh3d.mcp.command.shape.csg;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * BSP (Binary Space Partitioning) tree node for CSG operations.
 * Ported from csg.js.
 *
 * <p>Each node has a splitting plane, a list of coplanar polygons, and front/back subtrees.
 * The tree is built incrementally by adding polygons.</p>
 *
 * <p>All tree traversals use iterative algorithms with explicit stacks to avoid
 * StackOverflowError on deep trees (e.g. sphere geometry with 200+ levels).</p>
 */
final class CsgNode {

    CsgPlane plane;
    CsgNode front;
    CsgNode back;
    List<CsgPolygon> polygons = new ArrayList<>();

    /** Deadline timestamp (ms). 0 = no timeout. Shared across all nodes via operations. */
    private long deadline;

    CsgNode() {
    }

    CsgNode(List<CsgPolygon> polygons) {
        build(polygons);
    }

    /** Sets the deadline for all operations on this tree. */
    void setDeadline(long deadlineMs) {
        this.deadline = deadlineMs;
    }

    private void checkDeadline() {
        if (deadline > 0 && System.currentTimeMillis() > deadline) {
            throw new CsgTimeoutException("CSG operation timed out");
        }
    }

    /**
     * Inverts the BSP tree (swaps inside/outside). Iterative.
     */
    void invert() {
        Deque<CsgNode> stack = new ArrayDeque<>();
        stack.push(this);

        while (!stack.isEmpty()) {
            CsgNode node = stack.pop();

            for (int i = 0; i < node.polygons.size(); i++) {
                node.polygons.set(i, node.polygons.get(i).flip());
            }
            if (node.plane != null) {
                node.plane = node.plane.flip();
            }

            // Swap front/back
            CsgNode temp = node.front;
            node.front = node.back;
            node.back = temp;

            if (node.front != null) stack.push(node.front);
            if (node.back != null) stack.push(node.back);
        }
    }

    /**
     * Returns a list of all polygons in the tree. Iterative.
     */
    List<CsgPolygon> allPolygons() {
        List<CsgPolygon> result = new ArrayList<>();
        Deque<CsgNode> stack = new ArrayDeque<>();
        stack.push(this);

        while (!stack.isEmpty()) {
            CsgNode node = stack.pop();
            result.addAll(node.polygons);
            if (node.front != null) stack.push(node.front);
            if (node.back != null) stack.push(node.back);
        }
        return result;
    }

    /**
     * Clips polygons to this BSP tree, removing polygons (or parts) that are
     * inside (behind) the solid represented by this tree. Iterative.
     */
    List<CsgPolygon> clipPolygons(List<CsgPolygon> inputPolygons) {
        List<CsgPolygon> result = new ArrayList<>();
        Deque<Object[]> stack = new ArrayDeque<>();
        stack.push(new Object[]{this, inputPolygons});

        while (!stack.isEmpty()) {
            checkDeadline();
            Object[] task = stack.pop();
            CsgNode node = (CsgNode) task[0];
            @SuppressWarnings("unchecked")
            List<CsgPolygon> polys = (List<CsgPolygon>) task[1];

            if (node.plane == null) {
                result.addAll(polys);
                continue;
            }

            List<CsgPolygon> frontList = new ArrayList<>();
            List<CsgPolygon> backList = new ArrayList<>();

            for (CsgPolygon p : polys) {
                node.plane.splitPolygon(p, frontList, backList, frontList, backList);
            }

            if (node.front != null) {
                stack.push(new Object[]{node.front, frontList});
            } else {
                result.addAll(frontList);
            }

            if (node.back != null) {
                stack.push(new Object[]{node.back, backList});
            }
            // If back is null, backList is discarded (clipped away)
        }

        return result;
    }

    /**
     * Clips this BSP tree against another BSP tree.
     * Removes polygons from this tree that are inside the other tree. Iterative.
     */
    void clipTo(CsgNode other) {
        Deque<CsgNode> stack = new ArrayDeque<>();
        stack.push(this);

        while (!stack.isEmpty()) {
            checkDeadline();
            CsgNode node = stack.pop();
            node.polygons = other.clipPolygons(node.polygons);
            if (node.front != null) stack.push(node.front);
            if (node.back != null) stack.push(node.back);
        }
    }

    /**
     * Builds the BSP tree by inserting polygons. Iterative.
     */
    void build(List<CsgPolygon> inputPolygons) {
        if (inputPolygons == null || inputPolygons.isEmpty()) return;

        Deque<Object[]> stack = new ArrayDeque<>();
        stack.push(new Object[]{this, inputPolygons});

        while (!stack.isEmpty()) {
            checkDeadline();
            Object[] task = stack.pop();
            CsgNode node = (CsgNode) task[0];
            @SuppressWarnings("unchecked")
            List<CsgPolygon> polys = (List<CsgPolygon>) task[1];

            if (polys.isEmpty()) continue;

            if (node.plane == null) {
                node.plane = polys.get(0).plane;
            }
            if (node.plane == null) continue;

            List<CsgPolygon> frontList = new ArrayList<>();
            List<CsgPolygon> backList = new ArrayList<>();

            for (CsgPolygon p : polys) {
                node.plane.splitPolygon(p, node.polygons, node.polygons, frontList, backList);
            }

            if (!frontList.isEmpty()) {
                if (node.front == null) node.front = new CsgNode();
                stack.push(new Object[]{node.front, frontList});
            }
            if (!backList.isEmpty()) {
                if (node.back == null) node.back = new CsgNode();
                stack.push(new Object[]{node.back, backList});
            }
        }
    }
}
