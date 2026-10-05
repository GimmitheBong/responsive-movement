package com.responsivemovement;

import org.junit.Test;
import static org.junit.Assert.*;

public class CombatApproachTest
{
    @Test
    public void shortbowCrossbowAndBlowpipeUseTheirReachAndLongrangeExtension()
    {
        assertEquals(7, CombatApproach.reserve("Magic shortbow", "Rapid", true, 1));
        assertEquals(9, CombatApproach.reserve("Magic shortbow (i)", "Longrange", true, 2));
        assertEquals(7, CombatApproach.reserve("Rune crossbow", "Accurate", true, 0));
        assertEquals(5, CombatApproach.reserve("Toxic blowpipe", "Rapid", true, 1));
        assertEquals(7, CombatApproach.reserve("Toxic blowpipe", "Longrange", true, 2));
        assertEquals(10, CombatApproach.reserve("Magic longbow", "Longrange", true, 2));
    }

    @Test
    public void shorterThrownWeaponsAndPoisonVariantsDoNotInheritBowRange()
    {
        assertEquals(3, CombatApproach.reserve("Rune dart(p++)", "Rapid", true, 1));
        assertEquals(4, CombatApproach.reserve("Dragon knife(p)", "Accurate", true, 0));
        assertEquals(6, CombatApproach.reserve("Dragon knife(p+)", "Longrange", true, 2));
        assertEquals(4, CombatApproach.reserve("Red chinchompa", "Accurate", true, 0));
    }

    @Test
    public void meleeReservesLongerReachAndCastingOrUnknownProfilesAreConservative()
    {
        assertEquals(2, CombatApproach.reserve("Crystal halberd", "Controlled", false, 2));
        assertEquals(2, CombatApproach.reserve("", "Accurate", false, 0));
        assertEquals(10, CombatApproach.reserve("Staff of air", "Defensive", false, 4));
        assertEquals(10, CombatApproach.reserve("New bow variant", "Rapid", true, 1));
        assertEquals(10, CombatApproach.reserve("Magic shortbow", "Other", true, 0));
        assertEquals(10, CombatApproach.reserve("", "", false, -1));
    }
}
