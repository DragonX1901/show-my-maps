package org.dx.show_my_maps.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Works out which map slots touch, and how large each map has to be drawn so the
 * gap between slots disappears. Adapted from Connected Map Arts 1.1.0 (MIT).
 */
public final class TileLayout {
    private TileLayout() {
    }

    public static Map<Integer, Tile> compute(List<Cell> cells) {
        Map<Position, Cell> grid = new HashMap<>();
        Map<Object, Integer> pitchX = new HashMap<>();
        Map<Object, Integer> pitchY = new HashMap<>();
        Set<Position> duplicates = new HashSet<>();

        for (Cell c : cells) {
            Position p = new Position(c.area(), c.x(), c.y());

            if (grid.putIfAbsent(p, c) != null) {
                duplicates.add(p);
            }

            for (Cell b : cells) {
                if (Objects.equals(c.area(), b.area())) {
                    int dx = b.x() - c.x();
                    int dy = b.y() - c.y();

                    if (dy == 0 && dx >= 16 && dx <= 20) {
                        pitchX.merge(c.area(), dx, Math::min);
                    }

                    if (dx == 0 && dy >= 16 && dy <= 20) {
                        pitchY.merge(c.area(), dy, Math::min);
                    }
                }
            }
        }

        duplicates.forEach(grid::remove);
        Map<Integer, boolean[]> axes = new HashMap<>();

        for (Cell seed : cells) {
            if (seed.map() && !axes.containsKey(seed.id())
                && !duplicates.contains(new Position(seed.area(), seed.x(), seed.y()))) {
                boolean[] used = new boolean[2];
                ArrayDeque<Cell> queue = new ArrayDeque<>();
                queue.add(seed);
                axes.put(seed.id(), used);
                int px = pitchX.getOrDefault(seed.area(), 0);
                int py = pitchY.getOrDefault(seed.area(), 0);

                while (!queue.isEmpty()) {
                    Cell c = queue.removeFirst();
                    int[][] steps = {{-px, 0}, {px, 0}, {0, -py}, {0, py}};

                    for (int[] step : steps) {
                        if (step[0] == 0 && step[1] == 0) {
                            continue;
                        }

                        Cell other = grid.get(new Position(c.area(), c.x() + step[0], c.y() + step[1]));

                        if (other != null && other.map()) {
                            used[step[0] != 0 ? 0 : 1] = true;

                            if (axes.putIfAbsent(other.id(), used) == null) {
                                queue.add(other);
                            }
                        }
                    }
                }
            }
        }

        Map<Integer, Tile> result = new HashMap<>();

        for (Cell c : cells) {
            if (!c.map() || duplicates.contains(new Position(c.area(), c.x(), c.y()))) {
                continue;
            }

            int px = pitchX.getOrDefault(c.area(), 0);
            int py = pitchY.getOrDefault(c.area(), 0);
            boolean left = px > 0 && map(grid, c, -px, 0);
            boolean right = px > 0 && map(grid, c, px, 0);
            boolean top = py > 0 && map(grid, c, 0, -py);
            boolean bottom = py > 0 && map(grid, c, 0, py);

            if (!left && !right && !top && !bottom) {
                continue;
            }

            float leftPad = left ? (px - 15) / 2 : 0.0F;
            float rightPad = right ? (px - 16) / 2 : 0.0F;
            float topPad = top ? (py - 15) / 2 : 0.0F;
            float bottomPad = bottom ? (py - 16) / 2 : 0.0F;
            Rect bounds = new Rect(c.x() - leftPad, c.y() - topPad,
                16.0F + leftPad + rightPad, 16.0F + topPad + bottomPad);
            List<Rect> clips = new ArrayList<>();
            clips.add(new Rect(bounds.x(), c.y(), bounds.width(), 16.0F));

            if (topPad > 0.0F) {
                float padLeft = left && map(grid, c, -px, -py) ? leftPad : 0.0F;
                float padRight = right && map(grid, c, px, -py) ? rightPad : 0.0F;
                clips.add(new Rect(c.x() - padLeft, c.y() - topPad, 16.0F + padLeft + padRight, topPad));
            }

            if (bottomPad > 0.0F) {
                float padLeft = left && map(grid, c, -px, py) ? leftPad : 0.0F;
                float padRight = right && map(grid, c, px, py) ? rightPad : 0.0F;
                clips.add(new Rect(c.x() - padLeft, c.y() + 16, 16.0F + padLeft + padRight, bottomPad));
            }

            boolean[] used = axes.get(c.id());
            float width = used[0] ? px : 16.0F;
            float height = used[1] ? py : 16.0F;
            // The image is centred on the slot, so its outer edges spill into the
            // gap between slots and the per-slot clips decide which part is visible.
            Rect image = new Rect(c.x() - ((int) width - 15) / 2, c.y() - ((int) height - 15) / 2, width, height);
            result.put(c.id(), new Tile(image, List.copyOf(clips)));
        }

        return result;
    }

    private static boolean map(Map<Position, Cell> grid, Cell c, int dx, int dy) {
        Cell other = grid.get(new Position(c.area(), c.x() + dx, c.y() + dy));
        return other != null && other.map();
    }

    public record Cell(int id, int x, int y, Object area, boolean map) {
    }

    private record Position(Object area, int x, int y) {
    }

    public record Rect(float x, float y, float width, float height) {
    }

    public record Tile(Rect bounds, List<Rect> clips) {
    }
}
