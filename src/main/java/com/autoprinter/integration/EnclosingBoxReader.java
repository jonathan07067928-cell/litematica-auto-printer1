package com.autoprinter.integration;

import com.autoprinter.LitematicaAutoPrinter;
import net.minecraft.core.Vec3i;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reads a placement's enclosing box without depending on the exact class that
 * Litematica returns, because that class differs between Litematica versions.
 * It finds the method by name and understands both known box shapes
 * (two corner positions, or min/max integer coordinates). If neither works it
 * logs one clear warning and returns null; the mod then falls back to scanning
 * the area around the player.
 */
final class EnclosingBoxReader {
    private static boolean warned;

    private EnclosingBoxReader() {
    }

    static Bounds read(Object placement) {
        try {
            Method method = findBoxMethod(placement.getClass());
            if (method == null) {
                return fail("no enclosing-box method found on " + placement.getClass().getName());
            }
            Object box = method.invoke(placement);
            if (box == null) {
                return null;
            }
            Bounds bounds = fromCorners(box);
            if (bounds == null) {
                bounds = fromCoordinates(box);
            }
            if (bounds == null) {
                return fail("unknown box type " + box.getClass().getName());
            }
            return bounds;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LitematicaAutoPrinter.LOGGER.error("Could not read the schematic bounds", e);
            return null;
        }
    }

    private static Method findBoxMethod(Class<?> type) {
        for (Method method : type.getMethods()) {
            if (method.getParameterCount() == 0
                    && method.getReturnType() != void.class
                    && method.getName().toLowerCase().contains("nclosingbox")) {
                return method;
            }
        }
        return null;
    }

    /** Box shape 1: getPos1() / getPos2() returning block positions. */
    private static Bounds fromCorners(Object box) throws ReflectiveOperationException {
        Method first;
        Method second;
        try {
            first = box.getClass().getMethod("getPos1");
            second = box.getClass().getMethod("getPos2");
        } catch (NoSuchMethodException e) {
            return null;
        }
        Object a = first.invoke(box);
        Object b = second.invoke(box);
        if (a instanceof Vec3i p1 && b instanceof Vec3i p2) {
            return Bounds.ofCorners(p1.getX(), p1.getY(), p1.getZ(), p2.getX(), p2.getY(), p2.getZ());
        }
        return null;
    }

    /** Box shape 2: minX..maxZ as fields or accessor methods. */
    private static Bounds fromCoordinates(Object box) {
        Integer minX = readInt(box, "minX");
        Integer minY = readInt(box, "minY");
        Integer minZ = readInt(box, "minZ");
        Integer maxX = readInt(box, "maxX");
        Integer maxY = readInt(box, "maxY");
        Integer maxZ = readInt(box, "maxZ");
        if (minX == null || minY == null || minZ == null || maxX == null || maxY == null || maxZ == null) {
            return null;
        }
        return Bounds.ofCorners(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Integer readInt(Object target, String name) {
        try {
            Field field = target.getClass().getField(name);
            return field.getInt(target);
        } catch (ReflectiveOperationException ignored) {
            // not a public field, try accessor methods below
        }
        String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String methodName : new String[]{name, "get" + capitalized}) {
            try {
                Object value = target.getClass().getMethod(methodName).invoke(target);
                if (value instanceof Integer number) {
                    return number;
                }
            } catch (ReflectiveOperationException ignored) {
                // try the next name
            }
        }
        return null;
    }

    private static Bounds fail(String reason) {
        if (!warned) {
            warned = true;
            LitematicaAutoPrinter.LOGGER.warn(
                    "Could not read the schematic size ({}). Progress will only cover the area around you.", reason);
        }
        return null;
    }
}
