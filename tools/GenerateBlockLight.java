/*
 * Regenerates src/main/resources/block_light.json: for every Java block state, how much light it gives, how much
 * light it takes away and which parts of its sides stop light, as the Java game itself has it.
 *
 * Run this whenever the Java version Barrel targets changes, with the Java version that server needs:
 *
 *   java tools/GenerateBlockLight.java <server.jar> src/main/resources/block_light.json [<names.txt>]
 *
 * <server.jar> is the dedicated server of that Java version, as Mojang hands it out (the launcher's version json
 * links it). The game is started far enough to know its blocks, nothing else of it is run. With <names.txt> every
 * state is also written out by name next to its number, to check the numbers against the other block lists.
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class GenerateBlockLight {

    // A side of a block is looked at as 16 by 16 squares, the smallest step the shapes of blocks are made of
    private static final int GRID = 16;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java tools/GenerateBlockLight.java <server.jar> <block_light.json> [<names.txt>]");
            System.exit(1);
        }
        Path work = Files.createTempDirectory("block-light");
        try {
            run(Path.of(args[0]), Path.of(args[1]), args.length > 2 ? Path.of(args[2]) : null, work);
        } finally {
            try (Stream<Path> files = Files.walk(work)) {
                files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    // The server jar holds the game and the libraries it needs as jars of their own, listed in versions.list and
    // libraries.list
    private static List<URL> unpack(Path serverJar, Path work) throws IOException {
        List<URL> jars = new ArrayList<>();
        try (ZipFile zip = new ZipFile(serverJar.toFile())) {
            for (String list : new String[]{"versions", "libraries"}) {
                ZipEntry listEntry = zip.getEntry("META-INF/" + list + ".list");
                if (listEntry == null) {
                    throw new IOException("Not a server jar of a recent version: no META-INF/" + list + ".list");
                }
                String content = new String(zip.getInputStream(listEntry).readAllBytes(), StandardCharsets.UTF_8);
                for (String line : content.split("\n")) {
                    String[] parts = line.trim().split("\t");
                    if (parts.length < 3) {
                        continue;
                    }
                    Path target = work.resolve(list).resolve(parts[2]);
                    Files.createDirectories(target.getParent());
                    try (InputStream in = zip.getInputStream(zip.getEntry("META-INF/" + list + "/" + parts[2]))) {
                        Files.copy(in, target);
                    }
                    jars.add(target.toUri().toURL());
                }
            }
        }
        return jars;
    }

    private static void run(Path serverJar, Path output, Path names, Path work) throws Exception {
        URLClassLoader loader = new URLClassLoader(unpack(serverJar, work).toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
        Thread.currentThread().setContextClassLoader(loader);
        loader.loadClass("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
        loader.loadClass("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);
        Object version = loader.loadClass("net.minecraft.SharedConstants").getMethod("getCurrentVersion").invoke(null);
        String versionName = (String) version.getClass().getMethod("name").invoke(version);

        Object registry = loader.loadClass("net.minecraft.world.level.block.Block").getField("BLOCK_STATE_REGISTRY").get(null);
        int stateCount = (int) registry.getClass().getMethod("size").invoke(registry);
        Method byId = registry.getClass().getMethod("byId", int.class);

        Class<?> stateClass = loader.loadClass("net.minecraft.world.level.block.state.BlockState");
        Method getLightEmission = stateClass.getMethod("getLightEmission");
        Method getLightDampening = stateClass.getMethod("getLightDampening");
        Method canOcclude = stateClass.getMethod("canOcclude");
        Method useShapeForLightOcclusion = stateClass.getMethod("useShapeForLightOcclusion");
        Class<?> directionClass = loader.loadClass("net.minecraft.core.Direction");
        Method getFaceOcclusionShape = stateClass.getMethod("getFaceOcclusionShape", directionClass);
        Method getAxis = directionClass.getMethod("getAxis");
        Method toAabbs = loader.loadClass("net.minecraft.world.phys.shapes.VoxelShape").getMethod("toAabbs");
        Class<?> aabbClass = loader.loadClass("net.minecraft.world.phys.AABB");
        Field[] min = {aabbClass.getField("minX"), aabbClass.getField("minY"), aabbClass.getField("minZ")};
        Field[] max = {aabbClass.getField("maxX"), aabbClass.getField("maxY"), aabbClass.getField("maxZ")};
        // Down, up, north, south, west, east
        Object[] directions = directionClass.getEnumConstants();

        // The sides that stop light, each kept once. Nothing is the first, all of a side the second
        List<String> faces = new ArrayList<>();
        Map<String, Integer> faceIds = new HashMap<>();
        char[] none = new char[GRID * GRID / 4], all = new char[GRID * GRID / 4];
        Arrays.fill(none, '0');
        Arrays.fill(all, 'f');
        for (String face : new String[]{new String(none), new String(all)}) {
            faceIds.put(face, faces.size());
            faces.add(face);
        }

        List<int[]> states = new ArrayList<>();
        PrintWriter nameWriter = names == null ? null : new PrintWriter(Files.newBufferedWriter(names));
        for (int id = 0; id < stateCount; id++) {
            Object state = byId.invoke(registry, id);
            int[] light = new int[2 + directions.length];
            light[0] = (int) getLightEmission.invoke(state);
            light[1] = (int) getLightDampening.invoke(state);
            // Only a block that uses its shape for it stops light by its sides, as the light engine of the game has it
            if ((boolean) canOcclude.invoke(state) && (boolean) useShapeForLightOcclusion.invoke(state)) {
                for (int side = 0; side < directions.length; side++) {
                    Object shape = getFaceOcclusionShape.invoke(state, directions[side]);
                    // The two axes along the side
                    int axis = ((Enum<?>) getAxis.invoke(directions[side])).ordinal();
                    int u = axis == 0 ? 1 : 0, v = axis == 2 ? 1 : 2;
                    boolean[] covered = new boolean[GRID * GRID];
                    for (Object box : (List<?>) toAabbs.invoke(shape)) {
                        for (int i = 0; i < GRID; i++) {
                            for (int j = 0; j < GRID; j++) {
                                double centerU = (i + 0.5) / GRID, centerV = (j + 0.5) / GRID;
                                if (min[u].getDouble(box) <= centerU && centerU <= max[u].getDouble(box)
                                        && min[v].getDouble(box) <= centerV && centerV <= max[v].getDouble(box)) {
                                    covered[i * GRID + j] = true;
                                }
                            }
                        }
                    }
                    StringBuilder face = new StringBuilder();
                    for (int nibble = 0; nibble < GRID * GRID / 4; nibble++) {
                        int bits = 0;
                        for (int bit = 0; bit < 4; bit++) {
                            bits = bits << 1 | (covered[nibble * 4 + bit] ? 1 : 0);
                        }
                        face.append(Character.forDigit(bits, 16));
                    }
                    light[2 + side] = faceIds.computeIfAbsent(face.toString(), key -> {
                        faces.add(key);
                        return faces.size() - 1;
                    });
                }
            }
            states.add(light);
            if (nameWriter != null) {
                nameWriter.println(id + " " + state + " " + Arrays.toString(light));
            }
        }
        if (nameWriter != null) {
            nameWriter.close();
        }

        // Few states are not like many others. Each kind of them is written once, and each state as the number of
        // its kind, one byte for each in base64
        List<String> kinds = new ArrayList<>();
        Map<String, Integer> kindIds = new HashMap<>();
        byte[] stateKinds = new byte[stateCount];
        for (int id = 0; id < stateCount; id++) {
            String kind = Arrays.toString(states.get(id));
            int kindId = kindIds.computeIfAbsent(kind, key -> {
                kinds.add(key);
                return kinds.size() - 1;
            });
            if (kindId > 255) {
                throw new IllegalStateException("More than 256 kinds of light, they no longer fit a byte");
            }
            stateKinds[id] = (byte) kindId;
        }

        StringBuilder json = new StringBuilder();
        json.append("{\n  \"java_version\": \"").append(versionName).append("\",\n");
        json.append("  \"state_count\": ").append(stateCount).append(",\n");
        json.append("  \"_kinds\": \"light given, light taken away, then the sides down, up, north, south, west, east\",\n");
        json.append("  \"kinds\": [\n");
        for (int i = 0; i < kinds.size(); i++) {
            json.append("    ").append(kinds.get(i)).append(i + 1 < kinds.size() ? ",\n" : "\n");
        }
        json.append("  ],\n  \"_faces\": \"16 by 16 squares of a side, a bit for each that stops light\",\n");
        json.append("  \"faces\": [\n");
        for (int i = 0; i < faces.size(); i++) {
            json.append("    \"").append(faces.get(i)).append(i + 1 < faces.size() ? "\",\n" : "\"\n");
        }
        json.append("  ],\n  \"states\": \"").append(Base64.getEncoder().encodeToString(stateKinds)).append("\"\n}\n");
        Files.writeString(output, json.toString());

        long giving = states.stream().filter(light -> light[0] > 0).count();
        long shaped = states.stream().filter(light -> Arrays.stream(light, 2, light.length).anyMatch(face -> face != 0)).count();
        System.err.println("Java " + versionName + ": " + stateCount + " states, " + giving + " give light, " + shaped
                + " stop light by their sides, " + kinds.size() + " kinds, " + faces.size() + " different sides");
    }
}
