package local.jasper;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRPropertiesUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class JasperCompileCheck {
    private static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null ? current.getClass().getName() : message;
    }

    public static void main(String[] args) throws IOException {
        Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get("templates");
        Path outputRoot = Paths.get("target", "compiled");
        String runtimeClasspath = System.getProperty("java.class.path", "");

        JRPropertiesUtil.getInstance(DefaultJasperReportsContext.getInstance())
            .setProperty("net.sf.jasperreports.compiler.classpath", runtimeClasspath);

        if (!Files.exists(sourceRoot)) {
            throw new IllegalStateException("Source folder not found: " + sourceRoot);
        }

        List<Path> jrxmlFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".jrxml"))
                .forEach(jrxmlFiles::add);
        }

        if (jrxmlFiles.isEmpty()) {
            throw new IllegalStateException("No .jrxml files found to compile in: " + sourceRoot);
        }

        Files.createDirectories(outputRoot);

        List<String> failed = new ArrayList<>();

        for (Path jrxml : jrxmlFiles) {
            Path relative = sourceRoot.relativize(jrxml);
            Path outDir = outputRoot.resolve(relative).getParent();
            if (outDir != null) {
                Files.createDirectories(outDir);
            }

            Path jasperOut = outputRoot.resolve(relative.toString().replace(".jrxml", ".jasper"));

            try {
                JasperCompileManager.compileReportToFile(jrxml.toString(), jasperOut.toString());
                System.out.println("OK  : " + jrxml);
            } catch (JRException e) {
                failed.add(jrxml + " -> " + rootCauseMessage(e));
                System.err.println("FAIL: " + jrxml);
                e.printStackTrace(System.err);
            }
        }

        if (!failed.isEmpty()) {
            System.err.println("\nFailed to compile " + failed.size() + " JRXML file(s):");
            failed.forEach(item -> System.err.println("- " + item));
            throw new IllegalStateException("Jasper compile check failed");
        }

        System.out.println("\nAll JRXML files compiled successfully: " + jrxmlFiles.size());
    }
}
