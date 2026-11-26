package me.dreamvoid.mtweaks.compiler;

import com.google.auto.service.AutoService;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.xml.parsers.DocumentBuilderFactory;

import li.lingfeng.ltweaks.lib.XposedLoad;

/**
 * Generates:
 * <ul>
 *     <li>{@code me.dreamvoid.mtweaks.xposed.core.ModuleRegistry} for the API 102 entry point;</li>
 *     <li>{@code me.dreamvoid.mtweaks.prefs.PrefKeys} mapping string resource ids to preference keys.</li>
 * </ul>
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("li.lingfeng.ltweaks.lib.XposedLoad")
public class XposedProcessor extends AbstractProcessor {

    private Messager mMessager;
    private String mAppPath;
    private String mVariant;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        mMessager = processingEnv.getMessager();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        if (annotations.isEmpty()) {
            return false;
        }

        TypeElement xposedElement = null;
        for (TypeElement annotation : annotations) {
            if ("li.lingfeng.ltweaks.lib.XposedLoad".equals(annotation.getQualifiedName().toString())) {
                xposedElement = annotation;
                break;
            }
        }

        try {
            if (xposedElement != null) {
                generateModuleRegistry(xposedElement, env);
                generateFeatureScopes(xposedElement, env);
            }
            Map<Integer, String> keys = buildKeyMap();
            generatePrefKeys("me.dreamvoid.mtweaks.prefs.PrefKeys", keys);
        } catch (Exception e) {
            mMessager.printMessage(Diagnostic.Kind.ERROR,
                    "MTweaks annotation processing failed: " + e);
            e.printStackTrace();
        }
        return true;
    }

    // ------------------------------------------------------------------ API 102 registry

    private void generateModuleRegistry(TypeElement xposedElement, RoundEnvironment env) throws Exception {
        JavaFileObject genFile = processingEnv.getFiler()
                .createSourceFile("me.dreamvoid.mtweaks.xposed.core.ModuleRegistry");
        updateAppPath(genFile.toUri().toString());
        Writer writer = genFile.openWriter();

        writer.write("package me.dreamvoid.mtweaks.xposed.core;\n\n");
        writer.write("public final class ModuleRegistry {\n\n");
        writer.write("    private final java.util.Map<String, java.util.List<Class<?>>> mByPackage ="
                + " new java.util.HashMap<>();\n");
        writer.write("    private final java.util.List<Class<?>> mForAll = new java.util.ArrayList<>();\n\n");
        writer.write("    public ModuleRegistry() {\n        addModulesForAll();\n        addModules();\n    }\n\n");
        writer.write("    public java.util.List<Class<?>> getModulesFor(String packageName) {\n");
        writer.write("        java.util.List<Class<?>> result = new java.util.ArrayList<>(mForAll);\n");
        writer.write("        java.util.List<Class<?>> specific = mByPackage.get(packageName);\n");
        writer.write("        if (specific != null) {\n");
        writer.write("            for (Class<?> cls : specific) {\n");
        writer.write("                if (!result.contains(cls)) result.add(cls);\n");
        writer.write("            }\n");
        writer.write("        }\n");
        writer.write("        return result;\n    }\n\n");
        writer.write("    private void addForAll(Class<?> cls) {\n        mForAll.add(cls);\n    }\n\n");
        writer.write("    private void add(String packageName, Class<?> cls) {\n");
        writer.write("        java.util.List<Class<?>> list = mByPackage.get(packageName);\n");
        writer.write("        if (list == null) {\n            list = new java.util.ArrayList<>();\n");
        writer.write("            mByPackage.put(packageName, list);\n        }\n");
        writer.write("        list.add(cls);\n    }\n\n");

        writer.write("    private void addModulesForAll() {\n");
        for (Element element_ : env.getElementsAnnotatedWith(xposedElement)) {
            TypeElement element = (TypeElement) element_;
            XposedLoad load = element.getAnnotation(XposedLoad.class);
            if (load.packages().length == 0) {
                writer.write("        addForAll(" + element.getQualifiedName() + ".class);\n");
            }
        }
        writer.write("    }\n\n");

        writer.write("    private void addModules() {\n");
        for (Element element_ : env.getElementsAnnotatedWith(xposedElement)) {
            TypeElement element = (TypeElement) element_;
            XposedLoad load = element.getAnnotation(XposedLoad.class);
            for (String packageName : load.packages()) {
                // LSPosed maps the system server scope "android" to "system".
                String scope = "android".equals(packageName) ? "system" : packageName;
                writer.write("        add(\"" + scope + "\", "
                        + element.getQualifiedName() + ".class);\n");
            }
        }
        writer.write("    }\n");
        writer.write("}");
        writer.close();
    }

    // ------------------------------------------------------------------ scope suggestions

    /**
     * Generates {@code me.dreamvoid.mtweaks.prefs.FeatureScopes}, mapping every preference key of a
     * feature to the packages it has to be scoped in.
     */
    private void generateFeatureScopes(TypeElement xposedElement, RoundEnvironment env) throws Exception {
        Map<String, LinkedHashSet<String>> scopes = new LinkedHashMap<>();
        for (Element element_ : env.getElementsAnnotatedWith(xposedElement)) {
            TypeElement element = (TypeElement) element_;
            XposedLoad load = element.getAnnotation(XposedLoad.class);
            if (load.packages().length == 0) {
                // Features loaded for all packages have no fixed scope to request.
                continue;
            }
            List<String> keys = new ArrayList<>();
            Collections.addAll(keys, load.prefs());
            Collections.addAll(keys, load.uiKeys());
            for (String key : keys) {
                if (key == null || key.isEmpty()) {
                    continue;
                }
                LinkedHashSet<String> packages = scopes.computeIfAbsent(key, k -> new LinkedHashSet<>());
                for (String packageName : load.packages()) {
                    // LSPosed maps the system server package "android" to "system".
                    packages.add("android".equals(packageName) ? "system" : packageName);
                }
            }
        }

        JavaFileObject genFile = processingEnv.getFiler()
                .createSourceFile("me.dreamvoid.mtweaks.prefs.FeatureScopes");
        Writer writer = genFile.openWriter();
        writer.write("package me.dreamvoid.mtweaks.prefs;\n\n");
        writer.write("import java.util.Collections;\n");
        writer.write("import java.util.LinkedHashMap;\n");
        writer.write("import java.util.Map;\n\n");
        writer.write("/**\n");
        writer.write(" * Generated: packages requested from the Xposed framework per preference key.\n");
        writer.write(" */\n");
        writer.write("public final class FeatureScopes {\n\n");
        writer.write("    private static final Map<String, String[]> BY_KEY;\n\n");
        writer.write("    static {\n");
        writer.write("        Map<String, String[]> scopes = new LinkedHashMap<>();\n");
        for (Map.Entry<String, LinkedHashSet<String>> entry : scopes.entrySet()) {
            StringBuilder array = new StringBuilder();
            for (String packageName : entry.getValue()) {
                if (array.length() > 0) {
                    array.append(", ");
                }
                array.append('"').append(packageName).append('"');
            }
            writer.write("        scopes.put(\"" + entry.getKey() + "\", new String[]{" + array + "});\n");
        }
        writer.write("        BY_KEY = Collections.unmodifiableMap(scopes);\n");
        writer.write("    }\n\n");
        writer.write("    private FeatureScopes() {\n    }\n\n");
        writer.write("    /**\n");
        writer.write("     * Returns the packages the given preference key needs in the module scope.\n");
        writer.write("     *\n");
        writer.write("     * @param key preference key, may be {@code null}\n");
        writer.write("     * @return packages, empty when the key is unknown\n");
        writer.write("     */\n");
        writer.write("    public static String[] packagesFor(String key) {\n");
        writer.write("        String[] packages = key == null ? null : BY_KEY.get(key);\n");
        writer.write("        return packages == null ? new String[0] : packages.clone();\n");
        writer.write("    }\n");
        writer.write("}");
        writer.close();
    }

    // ------------------------------------------------------------------ pref keys

    private void generatePrefKeys(String className, Map<Integer, String> keys) throws Exception {
        JavaFileObject genFile = processingEnv.getFiler().createSourceFile(className);
        int dot = className.lastIndexOf('.');
        String packageName = className.substring(0, dot);
        String simpleName = className.substring(dot + 1);

        Writer writer = genFile.openWriter();
        writer.write("package " + packageName + ";\n\n");
        writer.write("import android.util.SparseArray;\n\n");
        writer.write("public class " + simpleName + " {\n\n");
        writer.write("    private static final SparseArray<String> ID_TO_KEY = new SparseArray<>("
                + keys.size() + ");\n\n");
        writer.write("    static {\n");
        for (Map.Entry<Integer, String> kv : keys.entrySet()) {
            writer.write("        ID_TO_KEY.put(" + kv.getKey() + ", \"" + kv.getValue() + "\");\n");
        }
        writer.write("    }\n\n");
        writer.write("    public static String getById(int id) {\n");
        writer.write("        return ID_TO_KEY.get(id);\n    }\n");
        writer.write("}");
        writer.close();
    }

    private Map<Integer, String> buildKeyMap() {
        Map<Integer, String> ids = new HashMap<>();
        Map<String, String> keys = new HashMap<>();

        File rFile = findRTxt();
        if (rFile != null) {
            try {
                String content = Files.readString(rFile.toPath());
                Pattern pattern = Pattern.compile("int string (key_\\w+) (0x[0-9a-fA-F]+)");
                Matcher matcher = pattern.matcher(content);
                matcher.results().forEach(result -> ids.put(
                        (int) Long.parseLong(result.group(2).substring(2), 16), result.group(1)));
                mMessager.printMessage(Diagnostic.Kind.NOTE,
                        "MTweaks: read " + ids.size() + " string ids from " + rFile);
            } catch (Throwable e) {
                mMessager.printMessage(Diagnostic.Kind.WARNING,
                        "MTweaks: can't read " + rFile + ": " + e);
            }
        } else {
            mMessager.printMessage(Diagnostic.Kind.WARNING, "MTweaks: R.txt not found");
        }

        File prefKeys = new File(mAppPath, "src/main/res/values/pref_keys.xml");
        if (prefKeys.exists()) {
            try {
                Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                        .parse(prefKeys);
                NodeList nodes = document.getDocumentElement().getChildNodes();
                for (int i = 0; i < nodes.getLength(); i++) {
                    Node node = nodes.item(i);
                    if (node.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    org.w3c.dom.Element element = (org.w3c.dom.Element) node;
                    String key = element.getAttribute("name");
                    if (!key.startsWith("key_") || element.getFirstChild() == null) {
                        continue;
                    }
                    keys.put(key, element.getFirstChild().getNodeValue());
                }
            } catch (Throwable e) {
                mMessager.printMessage(Diagnostic.Kind.WARNING,
                        "MTweaks: can't read pref_keys.xml: " + e);
            }
        }

        Map<Integer, String> result = new HashMap<>();
        for (Map.Entry<Integer, String> entry : ids.entrySet()) {
            String value = keys.get(entry.getValue());
            if (value != null) {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    /**
     * Reads the resource ids with their final values; the Java compile task depends on
     * {@code process<Variant>Resources} so this file exists during annotation processing.
     */
    private File findRTxt() {
        if (mAppPath == null) {
            return null;
        }
        Path base = Path.of(mAppPath, "build", "intermediates", "runtime_symbol_list");
        if (mVariant != null) {
            File variantFile = findRTxt(base.resolve(mVariant));
            if (variantFile != null) {
                return variantFile;
            }
        }
        return findRTxt(base);
    }

    private File findRTxt(Path root) {
        if (!Files.isDirectory(root)) {
            return null;
        }
        try (Stream<Path> stream = Files.walk(root, 5)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> "R.txt".equals(path.getFileName().toString()))
                    .filter(path -> !path.toString().contains("AndroidTest"))
                    .max(Comparator.comparingInt(this::countKeyStrings))
                    .filter(path -> countKeyStrings(path) > 0)
                    .map(Path::toFile)
                    .orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int countKeyStrings(Path path) {
        try {
            String content = Files.readString(path);
            int score = 0;
            int index = 0;
            while ((index = content.indexOf("int string key_", index)) >= 0) {
                score++;
                index += 10;
            }
            return score;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private void updateAppPath(String generatedFileUri) {
        if (mAppPath != null) {
            return;
        }
        try {
            File generated = new File(java.net.URI.create(generatedFileUri));
            File dir = generated.getParentFile();
            while (dir != null && !"build".equals(dir.getName())) {
                dir = dir.getParentFile();
            }
            if (dir != null && dir.getParentFile() != null) {
                mAppPath = dir.getParentFile().getAbsolutePath();
                mVariant = resolveVariant(generated.getAbsolutePath());
                mMessager.printMessage(Diagnostic.Kind.NOTE, "MTweaks: app path = " + mAppPath
                        + ", variant = " + mVariant);
            }
        } catch (Throwable e) {
            mMessager.printMessage(Diagnostic.Kind.WARNING,
                    "MTweaks: can't resolve app path from " + generatedFileUri + ": " + e);
        }
    }

    private String resolveVariant(String generatedPath) {
        String marker = "ap_generated_sources";
        int start = generatedPath.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int variantStart = start + marker.length();
        while (variantStart < generatedPath.length()
                && (generatedPath.charAt(variantStart) == '\\'
                || generatedPath.charAt(variantStart) == '/')) {
            variantStart++;
        }
        int variantEnd = generatedPath.indexOf(File.separatorChar, variantStart);
        return variantEnd > variantStart ? generatedPath.substring(variantStart, variantEnd) : null;
    }
}
