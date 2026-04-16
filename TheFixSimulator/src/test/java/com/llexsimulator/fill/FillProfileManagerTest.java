/*
 * Copyright (C) 2026 Debjyoti SARKAR
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.llexsimulator.fill;

import com.llexsimulator.sbe.FillBehaviorType;
import com.llexsimulator.sbe.RejectReason;
import com.llexsimulator.web.dto.FillProfileDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FillProfileManagerTest {

    @Test
    void seedsBuiltinProfilesAndTracksDefaultActiveName() {
        FillProfileManager manager = new FillProfileManager();

        assertEquals("immediate-full-fill", manager.getActiveProfileName());
        assertTrue(manager.getAllProfiles().size() >= 7);
        assertEquals(FillBehaviorType.IMMEDIATE_FULL_FILL, manager.getActiveConfig().behaviorType);
    }

    @Test
    void activateSwitchesToRequestedBuiltinProfile() {
        FillProfileManager manager = new FillProfileManager();

        manager.activate("reject-all");

        assertEquals("reject-all", manager.getActiveProfileName());
        assertEquals(FillBehaviorType.REJECT, manager.getActiveConfig().behaviorType);
        assertEquals(RejectReason.SIMULATOR_REJECT, manager.getActiveConfig().rejectReason);
    }

    @Test
    void createUpdateAndDeleteCustomProfiles() {
        FillProfileManager manager = new FillProfileManager();
        FillProfileDto dto = new FillProfileDto(
                "custom-random",
                "Custom random fill profile",
                "RANDOM_FILL",
                6_000,
                2,
                3,
                null,
                25,
                75,
                1,
                5,
                2);

        manager.createOrUpdate(dto);
        manager.activate("custom-random");

        FillBehaviorConfig config = manager.getActiveConfig();
        assertEquals("custom-random", manager.getActiveProfileName());
        assertEquals(FillBehaviorType.RANDOM_FILL, config.behaviorType);
        assertEquals(2_500, config.randomMinQtyPctBps);
        assertEquals(7_500, config.randomMaxQtyPctBps);
        assertEquals(1_000_000L, config.randomMinDelayNs);
        assertEquals(5_000_000L, config.randomMaxDelayNs);

        assertTrue(manager.delete("custom-random"));
        assertFalse(manager.delete("custom-random"));
    }

    @Test
    void activateSwitchesToRandomRejectCancelBuiltinProfile() {
        FillProfileManager manager = new FillProfileManager();

        manager.activate("random-reject-cancel");

        assertEquals("random-reject-cancel", manager.getActiveProfileName());
        assertEquals(FillBehaviorType.valueOf("RANDOM_REJECT_CANCEL"), manager.getActiveConfig().behaviorType);
        assertEquals(RejectReason.SIMULATOR_REJECT, manager.getActiveConfig().rejectReason);
    }
}
