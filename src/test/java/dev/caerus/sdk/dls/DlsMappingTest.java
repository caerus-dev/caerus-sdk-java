package dev.caerus.sdk.dls;

import dev.caerus.sdk.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class DlsMappingTest {

    @Test
    void modesGoAndComeBackWithoutLosingAnything() {
        for (LockMode mode : LockMode.values()) {
            assertThat(DlsMapping.toLockMode(DlsMapping.toGrpc(mode).getNumber())).contains(mode);
        }
    }

    @Test
    void aModeThatDoesNotExistDoesNotPassForAValidOne() {
        assertThat(DlsMapping.toGrpc(null).getNumber()).isZero();
        assertThat(DlsMapping.toLockMode(99)).isEmpty();
        assertThat(DlsMapping.toLockMode(0)).isEmpty();
    }

    @Test
    void translatesTheThreeStatesTheEngineDefines() {
        assertThat(DlsMapping.toLockStatus(1)).isEqualTo(LockStatus.ACQUIRED);
        assertThat(DlsMapping.toLockStatus(2)).isEqualTo(LockStatus.DENIED);
        assertThat(DlsMapping.toLockStatus(3)).isEqualTo(LockStatus.QUEUED);
    }

    @Test
    void whatItDoesNotRecogniseIsUnknownNeverDenied() {
        assertThat(DlsMapping.toLockStatus(0)).isEqualTo(LockStatus.UNKNOWN);
        assertThat(DlsMapping.toLockStatus(77)).isEqualTo(LockStatus.UNKNOWN);
    }

    @Test
    void aRealFencingTokenPassesAsIs() {
        assertThat(DlsMapping.decodeFencingToken(42)).hasValue(42);
        assertThat(DlsMapping.decodeFencingToken(9_007_199_254_740_993L)).hasValue(9_007_199_254_740_993L);
    }

    @Test
    void absenceIsNeverTurnedIntoZero() {
        assertThat(DlsMapping.decodeFencingToken(0)).isEmpty();
        assertThat(DlsMapping.decodeFencingToken(-1)).isEmpty();
    }

    @Test
    void letsAnAcquiredLockThrough() {
        LockHolder granted = new LockHolder("l1", OptionalLong.of(3), LockStatus.ACQUIRED);
        assertThat(DlsMapping.assertAcquired(granted, "ns", "k")).isSameAs(granted);
    }

    @Test
    void aDeniedLockThrowsWithItsReason() {
        LockDeniedError error = catchThrowableOfType(LockDeniedError.class, () ->
                DlsMapping.assertAcquired(new LockHolder("l1", OptionalLong.empty(), LockStatus.DENIED), "ns", "k"));

        assertThat(error.reason()).contains("LOCK_DENIED");
        assertThat(error.getMessage()).contains("ns/k");
    }

    @Test
    void anUnknownStatusSaysItDoesNotKnowNotThatSomeoneElseHasIt() {
        DlsError error = catchThrowableOfType(DlsError.class, () ->
                DlsMapping.assertAcquired(new LockHolder("l1", OptionalLong.empty(), LockStatus.UNKNOWN), "ns", "k"));

        assertThat(error).isNotInstanceOf(LockDeniedError.class);
        assertThat(error.code()).isEqualTo(ErrorCode.UNKNOWN);
    }

    @Test
    void aValidStatusThisCallCannotUseAlsoStops() {
        assertThatThrownBy(() ->
                DlsMapping.assertAcquired(new LockHolder("l1", OptionalLong.empty(), LockStatus.QUEUED), "ns", "k"))
                .isInstanceOf(DlsConflictError.class);
    }
}
