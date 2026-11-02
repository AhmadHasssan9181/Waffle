package com.noobdev.waffle.graphbuilder;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.util.DefaultFlagEncoderFactory;
import com.graphhopper.routing.util.EncodingManager;

import java.io.File;

/**
 * Builds the GraphHopper routing graph that ships in app/src/main/assets/graph-cache.
 *
 * Usage:
 *   gradlew :tools:graph-builder:run --args="path/to/area.osm path/to/output-graph"
 *
 * The profiles and CH setup here MUST match LocalRoutingEngine.kt exactly, otherwise the Android
 * engine will refuse to load the graph.
 */
public final class GraphBuilder {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: GraphBuilder <input.osm|input.osm.pbf> <output-graph-dir>");
            System.exit(1);
        }

        File input = new File(args[0]);
        if (!input.exists()) {
            System.err.println("Input file does not exist: " + input.getAbsolutePath());
            System.exit(2);
        }

        File output = new File(args[1]);
        if (output.exists()) {
            // GraphHopper treats an existing folder without a "properties" file as a corrupt graph,
            // so a clean build deletes it and lets the importer create it.
            deleteRecursively(output);
        }
        if (output.exists()) {
            System.err.println("Could not clean output directory: " + output.getAbsolutePath());
            System.exit(3);
        }

        System.out.println("Building graph");
        System.out.println("  input : " + input.getAbsolutePath());
        System.out.println("  output: " + output.getAbsolutePath());

        GraphHopper hopper = new GraphHopperOSM();
        hopper.setDataReaderFile(input.getAbsolutePath());
        hopper.setGraphHopperLocation(output.getAbsolutePath());
        hopper.setProfiles(
                new Profile("car").setVehicle("car").setWeighting("fastest"),
                new Profile("foot").setVehicle("foot").setWeighting("shortest"),
                new Profile("bike").setVehicle("bike").setWeighting("fastest")
        );
        // GraphHopper 1.0's load() tries to read an EncodingManager from the target folder before
        // falling back to an import, so a fresh import must provide one up front. This string must
        // contain every vehicle used by the profiles above.
        hopper.setEncodingManager(
                EncodingManager.create(new DefaultFlagEncoderFactory(), "car,foot,bike")
        );
        // GraphHopper's mobile mode does not allow disabling CH per request, so every profile
        // needs a CH preparation. The graph is a little larger, but all three travel modes work
        // offline.
        hopper.getCHPreparationHandler().setCHProfiles(
                new CHProfile("car"),
                new CHProfile("foot"),
                new CHProfile("bike")
        );
        hopper.setElevation(false);
        hopper.setStoreOnFlush(true);
        hopper.importOrLoad();
        hopper.close();

        System.out.println("Done. Copy the folder into app/src/main/assets/graph-cache/");
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            System.err.println("Warning: could not delete " + file.getAbsolutePath());
        }
    }
}
