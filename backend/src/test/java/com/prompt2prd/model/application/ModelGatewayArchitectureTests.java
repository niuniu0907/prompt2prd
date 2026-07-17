package com.prompt2prd.model.application;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelGatewayArchitectureTests {

    private static final List<String> BUSINESS_PACKAGES =
            List.of("analysis", "architecture", "generation");
    private static final List<String> FORBIDDEN_IMPORTS = List.of(
            "org.springframework.ai",
            "com.prompt2prd.model.adapter",
            "com.openai",
            "com.azure.ai.openai");

    @Test
    void modelGatewayContractDoesNotExposeVendorTypes() {
        for (Method method : ModelGateway.class.getDeclaredMethods()) {
            assertApplicationBoundary(method.getGenericReturnType());
            for (Type parameterType : method.getGenericParameterTypes()) {
                assertApplicationBoundary(parameterType);
            }
        }
    }

    @Test
    void businessPackagesDoNotImportModelAdaptersOrVendorClients() throws IOException {
        Path sourceRoot = Path.of(System.getProperty("basedir"), "src", "main", "java", "com", "prompt2prd");

        for (String packageName : BUSINESS_PACKAGES) {
            Path packageRoot = sourceRoot.resolve(packageName);
            if (!Files.exists(packageRoot)) {
                continue;
            }
            try (var files = Files.walk(packageRoot)) {
                for (Path sourceFile : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(sourceFile);
                    for (String forbiddenImport : FORBIDDEN_IMPORTS) {
                        assertThat(source)
                                .as("%s must use ModelGateway instead of %s", sourceFile, forbiddenImport)
                                .doesNotContain(forbiddenImport);
                    }
                }
            }
        }
    }

    private void assertApplicationBoundary(Type type) {
        if (type instanceof ParameterizedType parameterizedType) {
            assertApplicationBoundary(parameterizedType.getRawType());
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                assertApplicationBoundary(argument);
            }
            return;
        }
        String typeName = type.getTypeName();
        assertThat(typeName)
                .doesNotContain("org.springframework.ai", "openai", "azure.ai")
                .doesNotContain("com.prompt2prd.model.adapter");
    }
}
