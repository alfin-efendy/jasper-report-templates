package local.jasper;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRPropertiesUtil;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class JasperCompileCheck {
    private static void addClassLocation(Set<String> entries, Class<?> clazz) {
        try {
            CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                URL location = codeSource.getLocation();
                if (location != null && "file".equalsIgnoreCase(location.getProtocol())) {
                    try {
                        String path = Paths.get(location.toURI()).toString();
                        entries.add(path);
                    } catch (URISyntaxException e) {
                        entries.add(location.getPath());
                    }
                }
            }
        } catch (Exception ignored) {
            // Skip if unable to determine location
        }
    }

    private static String buildCompilerClasspath(ClassLoader classLoader) {
        Set<String> entries = new LinkedHashSet<>();

        // Add explicit locations of critical Jasper classes
        addClassLocation(entries, JasperCompileManager.class);
        addClassLocation(entries, JRException.class);
        addClassLocation(entries, DefaultJasperReportsContext.class);

        // Add system classpath
        String systemClasspath = System.getProperty("java.class.path", "");
        if (!systemClasspath.isBlank()) {
            String[] parts = systemClasspath.split(File.pathSeparator);
            for (String part : parts) {
                if (!part.isBlank()) {
                    entries.add(part);
                }
            }
        }

        // Collect from classloader hierarchy
        ClassLoader current = classLoader;
        while (current != null) {
            if (current instanceof URLClassLoader urlClassLoader) {
                for (URL url : urlClassLoader.getURLs()) {
                    if (!"file".equalsIgnoreCase(url.getProtocol())) {
                        continue;
                    }
                    try {
                        entries.add(Paths.get(url.toURI()).toString());
                    } catch (URISyntaxException ignored) {
                        entries.add(url.getPath());
                    }
                }
            }
            current = current.getParent();
        }

        return String.join(File.pathSeparator, entries);
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null ? current.getClass().getName() : message;
    }

    public static void main(String[] args) throws IOException {
        // Pre-load critical Jasper classes to ensure they're accessible
        try {
            Class.forName("net.sf.jasperreports.engine.fill.JREvaluator");
            Class.forName("net.sf.jasperreports.engine.fill.JRFillParameter");
            Class.forName("net.sf.jasperreports.engine.fill.JRFillField");
            System.out.println("Jasper fill classes pre-loaded successfully");
        } catch (ClassNotFoundException e) {
            System.err.println("WARNING: Could not pre-load Jasper fill classes: " + e.getMessage());
        }

        Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get("templates");
        Path outputRoot = Paths.get("target", "compiled");
        ClassLoader appClassLoader = JasperCompileCheck.class.getClassLoader();
        String runtimeClasspath = buildCompilerClasspath(appClassLoader);

        // Set compiler classpath in multiple ways to ensure it's picked up
        System.setProperty("net.sf.jasperreports.compiler.classpath", runtimeClasspath);
        JRPropertiesUtil properties = JRPropertiesUtil.getInstance(DefaultJasperReportsContext.getInstance());
        properties.setProperty("net.sf.jasperreports.compiler.classpath", runtimeClasspath);
        properties.setProperty("net.sf.jasperreports.compiler.keep.java.file", "true");
        
        System.err.println("DEBUG: Jasper compiler classpath entries:");
        for (String entry : runtimeClasspath.split(File.pathSeparator)) {
            System.err.println("  - " + entry);
        }

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
                Thread currentThread = Thread.currentThread();
                ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
                try {
                    currentThread.setContextClassLoader(appClassLoader);
                    JasperCompileManager.compileReportToFile(jrxml.toString(), jasperOut.toString());
                } finally {
                    currentThread.setContextClassLoader(originalContextClassLoader);
                }
                System.out.println("OK  : " + jrxml);
            } catch (JRException e) {
                failed.add(jrxml + " -> " + rootCauseMessage(e));
                System.err.println("FAIL: " + jrxml);
                e.printStackTrace(System.err);
            } catch (RuntimeException e) {
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
