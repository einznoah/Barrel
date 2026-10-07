/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The java accounts that are let in, for a proxy that only lets in those it is told of. They are in a file that is
// kept like the whitelist of a java server: a list of entries, each with the uuid of an account and its name. An
// account is told by its uuid alone. The name is there for whoever reads the file and is not looked at: an account
// can be given another name, and its old name can then be taken by somebody else
public class Whitelist {

    public static final String FILE = "whitelist.json";

    // A uuid as it is written, and as mojang gives it out: without the dashes
    private static final Pattern UUID_WRITTEN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern UUID_WITHOUT_DASHES = Pattern.compile("([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})");

    private final Path file;
    private Set<UUID> accounts = Set.of();
    // What the file was like when it was read. It is read again once it is not like that anymore
    private String read;

    public Whitelist(Path file) {
        this.file = file;
        if (!Files.exists(file)) {
            try {
                Files.writeString(file, "[]\n");
                System.out.println("There was no " + file + ", one that lists nobody was made. An entry looks like"
                        + " {\"uuid\": \"069a79f4-44e9-4726-a5be-fca90e38aaf5\", \"name\": \"Notch\"}, the entries go between the [ ] with a comma between them");
            } catch (IOException e) {
                System.out.println("There is no " + file + " and none can be made: " + e);
            }
        }
        this.read();
    }

    public synchronized boolean contains(UUID javaAccount) {
        // The file can be changed while the proxy runs
        if (!Objects.equals(this.getFileState(), this.read)) {
            this.read();
        }
        return this.accounts.contains(javaAccount);
    }

    // Tells the file as it is from the file as it was, null for a file that is not there
    private String getFileState() {
        try {
            return Files.getLastModifiedTime(this.file) + " " + Files.size(this.file);
        } catch (IOException e) {
            return null;
        }
    }

    private void read() {
        // Taken before the file is read: what is changed while it is read is read the next time
        this.read = this.getFileState();

        Set<UUID> accounts = new HashSet<>();
        try (Reader reader = Files.newBufferedReader(this.file)) {
            JsonElement list = JsonParser.parseReader(reader);
            // A file with nothing in it lists nobody
            if (!list.isJsonNull()) {
                int number = 0;
                for (JsonElement entry : list.getAsJsonArray()) {
                    number++;
                    UUID account = getAccount(entry);
                    if (account != null) {
                        accounts.add(account);
                    } else {
                        System.out.println("Entry " + number + " of the whitelist does not have the uuid of an account and is left out: " + entry);
                    }
                }
            }
        } catch (Exception e) {
            // Rather nobody than somebody that was taken off the list
            this.accounts = Set.of();
            // What is wrong with it is the first line of what the reader of json tells
            System.out.println("The whitelist " + this.file + " can not be read, no java account is let in until it can: " + String.valueOf(e).lines().findFirst().orElse(""));
            return;
        }

        this.accounts = accounts;
        System.out.println("The whitelist was read: " + (accounts.isEmpty() ? "nobody is on it, no java account is let in"
                : accounts.size() == 1 ? "1 java account is let in" : accounts.size() + " java accounts are let in"));
    }

    private static UUID getAccount(JsonElement entry) {
        if (!entry.isJsonObject()) {
            return null;
        }
        JsonElement uuid = entry.getAsJsonObject().get("uuid");
        if (uuid == null || !uuid.isJsonPrimitive()) {
            return null;
        }

        String written = uuid.getAsString().trim();
        Matcher withoutDashes = UUID_WITHOUT_DASHES.matcher(written);
        if (withoutDashes.matches()) {
            written = withoutDashes.replaceFirst("$1-$2-$3-$4-$5");
        }
        return UUID_WRITTEN.matcher(written).matches() ? UUID.fromString(written) : null;
    }
}
