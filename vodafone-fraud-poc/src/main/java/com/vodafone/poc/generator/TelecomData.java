package com.vodafone.poc.generator;

import java.util.Random;

public final class TelecomData {
    public static final String[] NORMAL_MSISDNS = {
            "905423184726", "905337092418", "905465781203", "905549230671",
            "905325678914", "905413902756", "905599843120", "905362471985"
    };

    public static final String[] FRAUD_MSISDNS = {
            "905429998877", "905339443322", "905466332211", "905548111111",
            "905327778899", "905414443311", "905599332200", "905362111100"
    };

    public static final String[] CELL_SITES = {
            "TR-IST-BES-0142", "TR-IST-KDK-0227", "TR-ANK-CAN-0084", "TR-IZM-KSK-0119",
            "TR-BRS-NIL-0061", "TR-ANT-MUR-0193", "TR-KOC-GEB-0048", "TR-ADN-SEY-0136"
    };

    public static final String[] HOME_LOCATIONS = {
            "Istanbul/Besiktas", "Istanbul/Kadikoy", "Ankara/Cankaya", "Izmir/Karsiyaka",
            "Bursa/Nilufer", "Antalya/Muratpasa", "Kocaeli/Gebze", "Adana/Seyhan"
    };

    public static final String[] IMPOSSIBLE_ROAMING_LOCATIONS = {
            "Berlin/DE", "Istanbul/TR", "Amsterdam/NL", "Ankara/TR",
            "Paris/FR", "Izmir/TR", "Madrid/ES", "Antalya/TR", "Milan/IT"
    };

    public static final String[] IMEI_TACS = {
            "35693810", "35976211", "35188912", "86740004", "86998105", "35345611"
    };

    private TelecomData() {}

    public static String normalMsisdn(Random random) {
        return NORMAL_MSISDNS[random.nextInt(NORMAL_MSISDNS.length)];
    }

    public static String fraudMsisdn(int index) {
        return FRAUD_MSISDNS[Math.floorMod(index, FRAUD_MSISDNS.length)];
    }

    public static String turkishMobile(Random random) {
        int[] prefixes = {542, 543, 544, 545, 546, 549, 532, 533, 534, 535, 536, 537, 551, 552, 553, 554, 555, 559};
        return String.format("90%d%07d", prefixes[random.nextInt(prefixes.length)], random.nextInt(10_000_000));
    }

    public static String sequentialCalleeBase(long sequence) {
        return String.valueOf(905550000000L + (sequence * 1000));
    }

    public static String cellSite(Random random) {
        return CELL_SITES[random.nextInt(CELL_SITES.length)];
    }

    public static String homeLocation(int index) {
        return HOME_LOCATIONS[Math.floorMod(index, HOME_LOCATIONS.length)];
    }

    public static String normalLocation(Random random) {
        return HOME_LOCATIONS[random.nextInt(HOME_LOCATIONS.length)];
    }

    public static String imei(Random random) {
        return IMEI_TACS[random.nextInt(IMEI_TACS.length)] + String.format("%07d", random.nextInt(10_000_000));
    }

    public static long voiceDurationSeconds(Random random) {
        int roll = random.nextInt(100);
        if (roll < 10) {
            return 0;
        }
        if (roll < 30) {
            return 5 + random.nextInt(25);
        }
        if (roll < 85) {
            return 30 + random.nextInt(270);
        }
        return 300 + random.nextInt(900);
    }

    public static double dataUsageMb(Random random) {
        int roll = random.nextInt(100);
        if (roll < 35) {
            return roundOneDecimal(random.nextDouble() * 25.0);
        }
        if (roll < 85) {
            return roundOneDecimal(25.0 + (random.nextDouble() * 450.0));
        }
        return roundOneDecimal(475.0 + (random.nextDouble() * 2500.0));
    }

    public static int simAgeDays(Random random) {
        int roll = random.nextInt(100);
        if (roll < 8) {
            return 1 + random.nextInt(7);
        }
        if (roll < 30) {
            return 8 + random.nextInt(90);
        }
        return 98 + random.nextInt(1500);
    }

    public static double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
