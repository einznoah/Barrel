/*
 *  ____                                 _
 * | __ )    __ _   _ __   _ __    ___  | |
 * |  _ \   / _` | | '__| | '__|  / _ \ | |
 * | |_) | | (_| | | |    | |    |  __/ | |
 * |____/   \__,_| |_|    |_|     \___| |_|
 *
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel;

import org.barrelmc.barrel.network.converter.BannerConverter;
import org.barrelmc.barrel.network.converter.BlockConverter;
import org.barrelmc.barrel.network.converter.EnchantmentConverter;
import org.barrelmc.barrel.network.converter.ItemConverter;
import org.barrelmc.barrel.network.converter.JavaRegistries;
import org.barrelmc.barrel.server.ProxyServer;

import java.io.InputStream;
import java.util.Properties;

public class Barrel {

    public static String DATA_PATH = System.getProperty("user.dir") + "/";

    // Tells a jar that was built again from one that was not
    private static String getBuildTime() {
        try (InputStream inputStream = Barrel.class.getClassLoader().getResourceAsStream("build.properties")) {
            Properties properties = new Properties();
            properties.load(inputStream);
            return properties.getProperty("buildTime");
        } catch (Exception e) {
            return "at an unknown time";
        }
    }

    public static void main(String[] args) {
        System.out.println("Starting Barrel Proxy software, built " + getBuildTime());
        // What a bedrock server sends at once may be more than the library takes of a client that is not known. The
        // proxy joins the server it was told to join, and a server sends the sub chunks it was asked for at once
        if (System.getProperty("bedrock.maxDecompressedBytes") == null) {
            System.setProperty("bedrock.maxDecompressedBytes", String.valueOf(64 * 1024 * 1024));
        }
        BlockConverter.init();
        ItemConverter.init();
        EnchantmentConverter.init();
        JavaRegistries.init();
        BannerConverter.init();
        new ProxyServer(DATA_PATH);
    }
}
