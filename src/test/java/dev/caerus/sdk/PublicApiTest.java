package dev.caerus.sdk;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PublicApiTest {

    private static final List<String> FORBIDDEN = List.of(
            "com.google.protobuf.",
            "com.google.rpc.",
            "com.google.gson.",
            "com.google.common.",
            "io.grpc.",
            "dev.caerus.sdk.internal.");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.caerus.sdk");
    }

    private static boolean isPublicApi(JavaClass type) {
        return type.getModifiers().contains(JavaModifier.PUBLIC)
                && !type.getPackageName().startsWith("dev.caerus.sdk.internal")
                && type.getEnclosingClass().map(PublicApiTest::isPublicApi).orElse(true);
    }

    private static boolean isVisible(Set<JavaModifier> modifiers) {
        return modifiers.contains(JavaModifier.PUBLIC) || modifiers.contains(JavaModifier.PROTECTED);
    }

    private static void check(String where, JavaType type, List<String> leaks) {
        for (JavaClass involved : type.getAllInvolvedRawTypes()) {
            String name = involved.getName();
            for (String prefix : FORBIDDEN) {
                if (name.startsWith(prefix)) {
                    leaks.add(where + " exposes " + name);
                }
            }
        }
    }

    @Test
    void nothingGeneratedFromTheProtoOrTheTransportReachesThePublicApi() {
        List<String> leaks = new ArrayList<>();

        for (JavaClass type : classes) {
            if (!isPublicApi(type)) {
                continue;
            }
            type.getRawSuperclass().ifPresent(superclass -> check(type.getName() + " extends", superclass, leaks));
            for (JavaType implemented : type.getInterfaces()) {
                check(type.getName() + " implements", implemented, leaks);
            }
            for (JavaField field : type.getFields()) {
                if (isVisible(field.getModifiers())) {
                    check(field.getFullName(), field.getType(), leaks);
                }
            }
            for (JavaCodeUnit unit : type.getCodeUnits()) {
                if (!isVisible(unit.getModifiers())) {
                    continue;
                }
                check(unit.getFullName() + " returns", unit.getReturnType(), leaks);
                for (JavaType parameter : unit.getParameterTypes()) {
                    check(unit.getFullName() + " takes", parameter, leaks);
                }
                for (JavaClass thrown : unit.getThrowsClause().getTypes()) {
                    check(unit.getFullName() + " throws", thrown, leaks);
                }
            }
        }

        assertThat(leaks).isEmpty();
    }

    @Test
    void thePublicPackagesAreTheOnesTheSdkPromises() {
        Set<String> packages = classes.stream()
                .filter(PublicApiTest::isPublicApi)
                .map(JavaClass::getPackageName)
                .collect(Collectors.toSet());

        assertThat(packages).containsExactlyInAnyOrder(
                "dev.caerus.sdk",
                "dev.caerus.sdk.sre",
                "dev.caerus.sdk.dls",
                "dev.caerus.sdk.webhooks");
    }

    @Test
    void everyErrorIsACaerusError() {
        List<String> strays = classes.stream()
                .filter(PublicApiTest::isPublicApi)
                .filter(type -> type.isAssignableTo(Throwable.class))
                .filter(type -> !type.isAssignableTo(CaerusError.class))
                .map(JavaClass::getName)
                .collect(Collectors.toList());

        assertThat(strays).isEmpty();
    }

    @Test
    void theVersionComesFromTheBuild() {
        assertThat(CaerusSdk.VERSION).isNotBlank().isNotEqualTo("unknown").doesNotContain("${");
    }
}
