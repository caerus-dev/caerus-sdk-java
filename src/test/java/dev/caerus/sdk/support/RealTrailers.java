package dev.caerus.sdk.support;

import dev.caerus.sdk.internal.ErrorDetails;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RealTrailers {

    public static final Map<String, String> SRE = new LinkedHashMap<>();
    public static final String DEADLOCK_DETECTED =
            "CAoSR1RyYW5zYWN0aW9uIGYzZDIzOWY2LWFiNWItNDkzNi05NDc3LThhYzQ5ZDMyZDA2YyBtYXJrZWQgQUJPUlRfUkVRVUVTVEVEGksKKHR5cGUuZ29vZ2xlYXBpcy5jb20vZ29vZ2xlLnJwYy5FcnJvckluZm8SHwoRREVBRExPQ0tfREVURUNURUQSCmNhZXJ1cy5kZXY=";

    static {
        SRE.put("OUT_OF_STOCK",
                "CAkSLk91dCBvZiBzdG9jayBmb3IgcmVzb3VyY2U6IGZ1bmNpb25ob3Jpem9udGVfRTgaRgoodHlwZS5nb29nbGVhcGlzLmNvbS9nb29nbGUucnBjLkVycm9ySW5mbxIaCgxPVVRfT0ZfU1RPQ0sSCmNhZXJ1cy5kZXY=");
        SRE.put("HOLDER_NOT_ACTIVE",
                "CAkSNVJlc291cmNlSG9sZGVyIGlzIG5vdCBpbiBhIHZhbGlkIHN0YXRlIHRvIGJlIGV4dGVuZGVkGksKKHR5cGUuZ29vZ2xlYXBpcy5jb20vZ29vZ2xlLnJwYy5FcnJvckluZm8SHwoRSE9MREVSX05PVF9BQ1RJVkUSCmNhZXJ1cy5kZXY=");
        SRE.put("RESOURCE_NOT_FOUND",
                "CAUSLlJlc291cmNlIG5vdCBmb3VuZCB3aXRoIGlkOiBub19leGlzdGVfamFtYXNfenoaTAoodHlwZS5nb29nbGVhcGlzLmNvbS9nb29nbGUucnBjLkVycm9ySW5mbxIgChJSRVNPVVJDRV9OT1RfRk9VTkQSCmNhZXJ1cy5kZXY=");
        SRE.put("TEMPLATE_NOT_FOUND",
                "CAUSNlRlbXBsYXRlIG5vdCBmb3VuZCB3aXRoIG5hbWU6IHBsYW50aWxsYV9pbmV4aXN0ZW50ZV96ehpMCih0eXBlLmdvb2dsZWFwaXMuY29tL2dvb2dsZS5ycGMuRXJyb3JJbmZvEiAKElRFTVBMQVRFX05PVF9GT1VORBIKY2FlcnVzLmRldg==");
        SRE.put("RESOURCE_HAS_ACTIVE_HOLDS",
                "CAkSPUNhbm5vdCBkZWxldGUgcmVzb3VyY2Ugd2l0aCBhY3RpdmUgaG9sZHM6IGZ1bmNpb25ob3Jpem9udGVfRTcaUwoodHlwZS5nb29nbGVhcGlzLmNvbS9nb29nbGUucnBjLkVycm9ySW5mbxInChlSRVNPVVJDRV9IQVNfQUNUSVZFX0hPTERTEgpjYWVydXMuZGV2");
    }

    private RealTrailers() {
    }

    public static byte[] bytes(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    public static StatusRuntimeException grpcError(Status.Code code, String message, byte[] detail) {
        Metadata trailers = new Metadata();
        if (detail != null) {
            trailers.put(ErrorDetails.STATUS_DETAILS_KEY, detail);
        }
        return Status.fromCode(code).withDescription(message).asRuntimeException(trailers);
    }

    public static StatusRuntimeException grpcError(Status.Code code, String message, String base64Detail) {
        return grpcError(code, message, base64Detail == null ? null : bytes(base64Detail));
    }

    public static StatusRuntimeException grpcError(Status.Code code, String message) {
        return grpcError(code, message, (byte[]) null);
    }
}
