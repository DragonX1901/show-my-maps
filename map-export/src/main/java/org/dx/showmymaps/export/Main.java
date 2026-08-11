package org.dx.showmymaps.export;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The command line around {@link Exporter}.
 *
 * <pre>
 *   java -jar ShowMyMaps-Export.jar &lt;world folder or backup .zip&gt; &lt;output folder&gt;
 * </pre>
 *
 * <p>Copy the output folder to any static web host and point the mod's art source at
 * it. Nothing needs to run on the server itself, and the world can stay shut down.
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        boolean asked = args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"));

        if (asked || args.length != 2) {
            System.err.println("""
                Publishes a world's filled maps so SHOW MY MAPS clients can fetch the
                pictures the server never sends them.

                  java -jar ShowMyMaps-Export.jar <world> <output>

                  <world>   a world folder, or a .zip backup of one
                  <output>  where to write <id>.bin and manifest.json

                Then serve <output> over https and set it as the art source for this
                server in the mod's settings.""");
            System.exit(asked ? 0 : 2);
            return;
        }

        try {
            Exporter.Result result = Exporter.run(Path.of(args[0]), Path.of(args[1]));

            for (String skipped : result.skipped()) {
                System.err.println("skipped " + skipped);
            }

            System.out.println("Exported " + result.exported() + " map(s) to " + args[1]);

            if (result.exported() == 0) {
                System.err.println("No map_<id>.dat files were found under " + args[0]
                    + ". Point this at the world folder itself, not the server root.");
                System.exit(1);
            }
        } catch (IOException e) {
            System.err.println("Could not export: " + e.getMessage());
            System.exit(1);
        }
    }
}
