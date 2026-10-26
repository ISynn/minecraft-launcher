package fr.launcher.ui.screens.account;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountScreenTest {

    @Test
    void identifiantAuFormatHabituel() {
        assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5", AccountScreen.dashed("069a79f444e94726a5befca90e38aaf5"));
        assertEquals("deja-formate", AccountScreen.dashed("deja-formate"));
    }
}
