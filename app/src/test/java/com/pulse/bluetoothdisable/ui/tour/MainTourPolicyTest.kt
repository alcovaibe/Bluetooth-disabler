package com.pulse.bluetoothdisable.ui.tour

import org.junit.Assert.assertEquals
import org.junit.Test

class MainTourPolicyTest {
    @Test fun newInstallationGetsOnlyTheUpdateTip() {
        assertEquals(MainTourKind.UPDATES, MainTourPolicy.decide(false, 100, 100))
    }
    @Test fun anExistingInstallationGetsTheFullTour() {
        assertEquals(MainTourKind.FULL, MainTourPolicy.decide(false, 100, 200))
    }
    @Test fun subsequentUpdatesNeverRepeatAClaimedTour() {
        assertEquals(MainTourKind.NONE, MainTourPolicy.decide(true, 100, 200))
        assertEquals(MainTourKind.NONE, MainTourPolicy.decide(true, 100, 10_000))
    }
    @Test fun anInvalidUpdateTimestampDoesNotExpandTheNewInstallTip() {
        assertEquals(MainTourKind.UPDATES, MainTourPolicy.decide(false, 200, 100))
    }
}
