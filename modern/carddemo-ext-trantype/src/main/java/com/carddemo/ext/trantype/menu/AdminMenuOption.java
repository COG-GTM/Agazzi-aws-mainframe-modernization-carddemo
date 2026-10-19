package com.carddemo.ext.trantype.menu;

import com.carddemo.contracts.screen.ScreenId;

/** One CDEMO-ADMIN-OPT entry of COADM02Y. */
public record AdminMenuOption(int number, String name, String program, ScreenId screen) {}
