package com.noobdev.waffle.graphbuilder;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.shapes.GHPoint;

import java.io.File;

/**
 * Loads a generated graph exactly the way the Android app does and routes a test pair.
 *
 * Usage:
 *   gradlew :tools:graph-builder:run -PmainClass=com.noobdev.waffle.graphbuilder.GraphVerifier --args="graph-dir"
 *
 * If this prints a distance, the asset copy will work offline on the device.
 */
public final class GraphVerifier {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: GraphVerifier <graph-dir>");
            System.exit(1);
        }

        File graph = new File(args[0]);
        if (!graph.isDirectory()) {
            System.err.println("Graph folder does not exist: " + graph.getAbsolutePath());
            System.exit(2);
        }

        GraphHopper hopper = new GraphHopper();
        hopper.forMobile();
        hopper.setGraphHopperLocation(graph.getAbsolutePath());
        hopper.setProfiles(
                new Profile("car").setVehicle("car").setWeighting("fastest"),
                new Profile("foot").setVehicle("foot").setWeighting("shortest"),
                new Profile("bike").setVehicle("bike").setWeighting("fastest")
        );
        hopper.getCHPreparationHandler().setCHProfiles(
                new CHProfile("car"),
                new CHProfile("foot"),
                new CHProfile("bike")
        );
        hopper.setAllowWrites(false);
        hopper.importOrLoad();

        // Islamabad city centre to the north-east of the city.
        for (String profile : new String[]{"car", "foot", "bike"}) {
            GHRequest request = new GHRequest(
                    new GHPoint(33.6844, 73.0479),
                    new GHPoint(33.7294, 73.0931)
            );
            request.setProfile(profile);
            request.setLocale("en");

            GHResponse response = hopper.route(request);
            if (response.hasErrors()) {
                System.err.println(profile + " routing failed: " + response.getErrors());
                System.exit(3);
            }

            System.out.println(profile + " distance: " + Math.round(response.getBest().getDistance()) + " m");
            System.out.println(profile + " time    : " + Math.round(response.getBest().getTime() / 1000.0) + " s");
            System.out.println(profile + " points  : " + response.getBest().getPoints().size());
            System.out.println(profile + " instructions: " + response.getBest().getInstructions().size());
        }
    }
}
