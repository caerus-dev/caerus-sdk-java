package dev.caerus.sdk.internal;

import dev.caerus.sdk.internal.proto.dls.DistributedLockingEngineGrpc;
import dev.caerus.sdk.internal.proto.sre.ExtendRequest;
import dev.caerus.sdk.internal.proto.sre.SharedResourceEngineGrpc;
import io.grpc.MethodDescriptor;
import io.grpc.ServiceDescriptor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedContractTest {

    private static List<String> methods(ServiceDescriptor service) {
        return service.getMethods().stream()
                .map(MethodDescriptor::getBareMethodName)
                .sorted()
                .collect(Collectors.toList());
    }

    @Test
    void theSreClientExposesEveryRpcTheServiceDeclares() {
        assertThat(methods(SharedResourceEngineGrpc.getServiceDescriptor())).containsExactly(
                "Confirm",
                "CreateResource",
                "DeleteResource",
                "Extend",
                "GetResource",
                "GetResourceHolder",
                "GetResourceHoldersList",
                "GetResourcesByGroupKey",
                "Release",
                "Take",
                "UpdateResource");
    }

    @Test
    void theDlsClientExposesEveryRpcTheServiceDeclares() {
        assertThat(methods(DistributedLockingEngineGrpc.getServiceDescriptor())).containsExactly(
                "AcquireLock",
                "BeginTransaction",
                "GetLockStatus",
                "GetTransactionStatus",
                "ReleaseLock",
                "ReleaseTransactionLocks",
                "RenewTransaction");
    }

    @Test
    void pointsAtTheRightPackageAndPath() {
        assertThat(SharedResourceEngineGrpc.getTakeMethod().getFullMethodName())
                .isEqualTo("caerus.sre.v1.SharedResourceEngine/Take");
        assertThat(DistributedLockingEngineGrpc.getAcquireLockMethod().getFullMethodName())
                .isEqualTo("caerus.dls.v1.DistributedLockingEngine/AcquireLock");
        assertThat(DistributedLockingEngineGrpc.getAcquireLockMethod().getType())
                .isEqualTo(MethodDescriptor.MethodType.SERVER_STREAMING);
    }

    @Test
    void takesTheExtensionInMilliseconds() {
        assertThat(ExtendRequest.getDescriptor().findFieldByName("extra_ms")).isNotNull();
        assertThat(ExtendRequest.getDescriptor().findFieldByName("extra_seconds")).isNull();
    }

    @Test
    void theProtoCopiesMatchTheChecksumsInTheirReadme() throws IOException, NoSuchAlgorithmException {
        String readme = Files.readString(Path.of("proto", "README.md"));
        for (String file : List.of("sre_service.proto", "dls_service.proto")) {
            byte[] content = Files.readAllBytes(Path.of("proto", file));
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            assertThat(readme).as(file).contains(sha);
        }
    }

    @Test
    void theGeneratedCodeLivesInTheInternalPackage() {
        assertThat(SharedResourceEngineGrpc.class.getPackageName()).isEqualTo("dev.caerus.sdk.internal.proto.sre");
        assertThat(DistributedLockingEngineGrpc.class.getPackageName()).isEqualTo("dev.caerus.sdk.internal.proto.dls");
    }
}
